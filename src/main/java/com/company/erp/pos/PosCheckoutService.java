package com.company.erp.pos;

import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.bank.BankResolver;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.customer.Customer;
import com.company.erp.customer.CustomerRepository;
import com.company.erp.finance.FinancialReferenceType;
import com.company.erp.finance.FinancialTransactionService;
import com.company.erp.finance.FinancialTransactionType;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.loan.LoanService;
import com.company.erp.notification.NotificationService;
import com.company.erp.notification.NotificationType;
import com.company.erp.payment.Payment;
import com.company.erp.payment.PaymentRepository;
import com.company.erp.payment.PaymentReferenceType;
import com.company.erp.pos.dto.CheckoutItemRequest;
import com.company.erp.pos.dto.CheckoutRequest;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.sales.PaymentStatus;
import com.company.erp.sales.Sale;
import com.company.erp.sales.SaleBatchAllocation;
import com.company.erp.sales.SaleItem;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.dto.SaleResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.Permission;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The single implementation of POS checkout (spec section 11). Every step
 * listed in the spec happens here, in this order, inside one
 * Transactional boundary:
 *
 * 1. Validate branch access        -> branchAccessService.assertCanWriteToBranch
 * 6/7. Create sale (placeholder totals) and persist it -> so it has a
 *      real id before stock moves, so those movements can reference it
 * 2/3/4/5. Validate stock & allocate, update inventory -> stockMutationService.issueStock
 *      (FIFO; throws InsufficientStockException, which rolls back everything)
 * 8. Create batch allocations       -> from the BatchConsumption list issueStock returns
 * 9. Create payment                 -> only if amountPaid greater than 0
 * 10. Create loan if necessary       -> loanService.createFromSale, only when
 *     remainingAmount greater than 0 (customer presence already enforced
 *     below before this point is ever reached).
 * 11. Create stock history           -> done inside issueStock
 * 12. Create financial transaction   -> financialTransactionService.record,
 *     once for SALE_REVENUE (the final discounted total), once for COGS
 *     (summed from every allocation across every item - never the selling
 *     price), and once for PAYMENT_IN when amountPaid is greater than
 *     zero. These are ledger entries only (see FinancialTransaction's
 *     javadoc) - the P&L itself is still computed straight from
 *     Sale/SaleBatchAllocation, not from these rows.
 *
 * If any step fails - insufficient stock on item 3 of 5, say - the whole
 * transaction rolls back: no sale, no items, no stock movement, no
 * payment, no loan survives. Nothing partial is ever left behind.
 */
@Service
@RequiredArgsConstructor
public class PosCheckoutService {

    private final BranchAccessService branchAccessService;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final StockMutationService stockMutationService;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final ProductBatchRepository productBatchRepository;
    private final LoanService loanService;
    private final FinancialTransactionService financialTransactionService;
    private final BankResolver bankResolver;
    private final NotificationService notificationService;

    @Transactional
    public SaleResponse checkout(CheckoutRequest request) {
        branchAccessService.assertCanWriteToBranch(request.branchId());

        // What this caller's role may do on the sale itself (set on the Roles page).
        var caller = branchAccessService.currentUser();
        if (request.discountAmount() != null && request.discountAmount().signum() > 0
                && !caller.hasPermission(Permission.POS_DISCOUNT)) {
            throw new ForbiddenException("Your role is not allowed to give a discount");
        }
        for (CheckoutItemRequest item : request.items()) {
            if (item.batchId() != null && !caller.hasPermission(Permission.POS_CHOOSE_BATCH)) {
                throw new ForbiddenException("Your role is not allowed to choose the batch to sell from");
            }
        }

        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User cashier = userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();

        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));
        }

        Sale sale = new Sale();
        sale.setSaleNumber(generateSaleNumber());
        sale.setBranch(branch);
        sale.setCustomer(customer);
        sale.setCreatedBy(cashier);
        sale.setTotalAmount(BigDecimal.ZERO);
        sale.setDiscountAmount(BigDecimal.ZERO);
        sale.setPaidAmount(BigDecimal.ZERO);
        sale.setRemainingAmount(BigDecimal.ZERO);
        sale.setPaymentStatus(PaymentStatus.CREDIT);
        sale = saleRepository.save(sale);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;

        for (CheckoutItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", itemRequest.productId()));

            // Products have no stored price - the cashier sets it on every line.
            BigDecimal unitPrice = itemRequest.unitPrice();
            if (unitPrice == null) {
                throw new BusinessRuleViolationException(
                        "Enter a selling price for '" + product.getName() + "'");
            }
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));
            subtotal = subtotal.add(lineTotal);

            // Optional: the cashier can pick the specific batch being sold. Otherwise FIFO.
            var consumptions = itemRequest.batchId() != null
                    ? stockMutationService.issueFromBatch(
                            product, branch, itemRequest.batchId(), itemRequest.quantity(), StockMovementType.SALE,
                            "POS sale " + sale.getSaleNumber(), cashier, StockReferenceType.SALE, sale.getId())
                    : stockMutationService.issueStock(
                            product, branch, itemRequest.quantity(), StockMovementType.SALE,
                            "POS sale " + sale.getSaleNumber(), cashier, StockReferenceType.SALE, sale.getId());

            SaleItem saleItem = new SaleItem();
            saleItem.setProduct(product);
            saleItem.setQuantity(itemRequest.quantity());
            saleItem.setUnitPrice(unitPrice);
            saleItem.setLineTotal(lineTotal);
            sale.addItem(saleItem);

            for (var consumption : consumptions) {
                SaleBatchAllocation allocation = new SaleBatchAllocation();
                allocation.setBatch(productBatchRepository.getReferenceById(consumption.batchId()));
                allocation.setQuantityAllocated(consumption.quantityConsumed());
                allocation.setUnitCost(consumption.unitCost());
                saleItem.addAllocation(allocation);
                totalCogs = totalCogs.add(
                        consumption.unitCost().multiply(BigDecimal.valueOf(consumption.quantityConsumed())));
            }
        }

        BigDecimal discount = request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessRuleViolationException("Discount cannot exceed the sale subtotal");
        }
        BigDecimal total = subtotal.subtract(discount);

        if (request.amountPaid().compareTo(total) > 0) {
            throw new BusinessRuleViolationException("Amount paid cannot exceed the sale total");
        }
        BigDecimal remaining = total.subtract(request.amountPaid());
        if (remaining.compareTo(BigDecimal.ZERO) > 0 && customer == null) {
            throw new BusinessRuleViolationException(
                    "A customer is required whenever a sale is not paid in full (credit/partial balance)");
        }

        sale.setTotalAmount(total);
        sale.setDiscountAmount(discount);
        sale.setPaidAmount(request.amountPaid());
        sale.setRemainingAmount(remaining);
        sale.setPaymentStatus(derivePaymentStatus(request.amountPaid(), total));

        if (request.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
            Payment payment = new Payment();
            payment.setMethod(request.paymentMethod());
            payment.setAmount(request.amountPaid());
            payment.setBranch(branch);
            payment.setReferenceType(PaymentReferenceType.SALE);
            payment.setReferenceId(sale.getId());
            // Links the chosen registered bank (or refuses a BANK payment with no bank at all).
            bankResolver.attach(payment, request.paymentMethod(), request.bankId(),
                    request.bankName(), request.accountReference());
            payment.setReceivedBy(cashier);
            paymentRepository.save(payment);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // customer == null was already rejected above whenever
            // remaining > 0, so this is always safe to call.
            loanService.createFromSale(sale, request.loanDueDate());
        }

        LocalDate today = LocalDate.now();
        financialTransactionService.record(branch, FinancialTransactionType.SALE_REVENUE, total, today,
                FinancialReferenceType.SALE, sale.getId());
        financialTransactionService.record(branch, FinancialTransactionType.COGS, totalCogs, today,
                FinancialReferenceType.SALE, sale.getId());
        if (request.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
            financialTransactionService.record(branch, FinancialTransactionType.PAYMENT_IN, request.amountPaid(),
                    today, FinancialReferenceType.SALE, sale.getId());
        }

        notificationService.record(NotificationType.SALE_COMPLETED, "New sale",
                sale.getSaleNumber() + " · " + request.items().size()
                        + (request.items().size() == 1 ? " item" : " items")
                        + " · total " + total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " · " + sale.getPaymentStatus().name().toLowerCase() + " at " + branch.getName(),
                branch, cashier, "SALE", sale.getId());

        return SaleResponse.from(sale);
    }

    private PaymentStatus derivePaymentStatus(BigDecimal amountPaid, BigDecimal total) {
        if (amountPaid.compareTo(total) >= 0) {
            return PaymentStatus.PAID;
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return PaymentStatus.PARTIAL;
        }
        return PaymentStatus.CREDIT;
    }

    private String generateSaleNumber() {
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            int suffix = ThreadLocalRandom.current().nextInt(10000, 99999);
            candidate = "SALE-" + stamp + "-" + suffix;
        } while (saleRepository.existsBySaleNumber(candidate));
        return candidate;
    }
}
