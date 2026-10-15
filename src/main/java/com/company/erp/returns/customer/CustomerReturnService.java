package com.company.erp.returns.customer;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.finance.FinancialReferenceType;
import com.company.erp.finance.FinancialTransactionService;
import com.company.erp.finance.FinancialTransactionType;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.loan.Loan;
import com.company.erp.loan.LoanRepository;
import com.company.erp.loan.LoanService;
import com.company.erp.returns.ReturnStatus;
import com.company.erp.returns.customer.dto.CreateCustomerReturnRequest;
import com.company.erp.returns.customer.dto.CustomerReturnResponse;
import com.company.erp.returns.customer.dto.CustomerReturnSummaryResponse;
import com.company.erp.sales.Sale;
import com.company.erp.sales.SaleBatchAllocation;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer returns (spec section 21): Customer -> original Sale ->
 * returned items -> validate -> restock if appropriate -> update
 * batch/inventory -> stock history -> reverse financial impact. The
 * reversal posts its own signed ledger entries (negative SALE_REVENUE,
 * negative COGS for the restocked portion) rather than editing the
 * original sale's entries - see FinancialTransaction's javadoc. Note the
 * P&L itself still nets these out via CustomerReturnItemRepository
 * queries directly against CustomerReturn/CustomerReturnItem, not by
 * summing this ledger.
 * <p>
 * The refund side of the reversal is loan-aware: if the sale was (partly)
 * paid on credit, the refund first writes down the associated Loan (see
 * LoanService.reduceForReturn) rather than treating the whole amount as
 * cash going back out - only whatever exceeds the loan's balance posts as
 * a real PAYMENT_OUT, since that portion is the only part actually
 * collected as cash/bank in the first place.
 * <p>
 * The whole return is one @Transactional method, exactly like POS
 * checkout - there's no PENDING/APPROVED staging (see ReturnStatus).
 */
@Service
@RequiredArgsConstructor
public class CustomerReturnService {

    private final CustomerReturnRepository customerReturnRepository;
    private final CustomerReturnItemRepository customerReturnItemRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;
    private final FinancialTransactionService financialTransactionService;
    private final LoanRepository loanRepository;
    private final LoanService loanService;

    @Transactional
    public CustomerReturnResponse create(CreateCustomerReturnRequest request) {
        Sale sale = saleRepository.findById(request.saleId())
                .orElseThrow(() -> ResourceNotFoundException.of("Sale", request.saleId()));
        branchAccessService.assertCanWriteToBranch(sale.getBranch().getId());

        User processor = currentUserEntity();

        CustomerReturn customerReturn = new CustomerReturn();
        customerReturn.setSale(sale);
        customerReturn.setCustomer(sale.getCustomer());
        customerReturn.setBranch(sale.getBranch());
        customerReturn.setStatus(ReturnStatus.COMPLETED);
        customerReturn.setProcessedBy(processor);
        customerReturn.setRefundAmount(BigDecimal.ZERO);

        BigDecimal totalRefund = BigDecimal.ZERO;
        BigDecimal totalReversedCogs = BigDecimal.ZERO;

        for (var itemRequest : request.items()) {
            SaleBatchAllocation allocation = findAllocationOnSale(sale, itemRequest.saleBatchAllocationId());

            int alreadyReturned = customerReturnItemRepository.sumReturnedForAllocation(allocation.getId());
            int stillReturnable = allocation.getQuantityAllocated() - alreadyReturned;
            if (itemRequest.quantity() > stillReturnable) {
                throw new BusinessRuleViolationException(
                        "Cannot return " + itemRequest.quantity() + " units from batch " +
                                allocation.getBatch().getBatchNumber() + " - only " + stillReturnable +
                                " of the original " + allocation.getQuantityAllocated() + " remain returnable");
            }

            // Refund at what the customer actually PAID (the SaleItem's
            // unitPrice), never the allocation's unitCost (what the
            // business paid the supplier) - those are different numbers
            // for a reason: one is revenue, the other is COGS.
            BigDecimal unitPricePaid = allocation.getSaleItem().getUnitPrice();
            BigDecimal lineRefund = unitPricePaid.multiply(BigDecimal.valueOf(itemRequest.quantity()));

            CustomerReturnItem item = new CustomerReturnItem();
            item.setSaleBatchAllocation(allocation);
            item.setQuantityReturned(itemRequest.quantity());
            item.setRestocked(itemRequest.restockOrDefault());
            item.setRefundAmount(lineRefund);
            customerReturn.addItem(item);

            totalRefund = totalRefund.add(lineRefund);

            if (item.isRestocked()) {
                stockMutationService.restockToBatch(
                        allocation.getSaleItem().getProduct(), sale.getBranch(),
                        allocation.getBatch().getId(), itemRequest.quantity(),
                        StockMovementType.CUSTOMER_RETURN,
                        "Customer return against sale " + sale.getSaleNumber(),
                        processor, StockReferenceType.CUSTOMER_RETURN, sale.getId());
                // Only the restocked portion reverses COGS - see this
                // class's javadoc and CustomerReturnItemRepository's
                // sumReversedCogsForBranchesInRange for why.
                totalReversedCogs = totalReversedCogs.add(
                        allocation.getUnitCost().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            }
            // restocked == false: written off - no stock movement, refund only.
        }

        customerReturn.setRefundAmount(totalRefund);
        CustomerReturn saved = customerReturnRepository.save(customerReturn);

        // Reverse the financial impact. Revenue is reversed for the full
        // returned amount regardless of how the sale was paid - it was
        // recognized in full at sale time (accrual), so returning goods
        // reverses it in full. What happens on the OTHER side of that
        // entry depends on how the sale was paid: if it was (partly) on
        // credit, the customer's debt shrinks first (a write-down, not a
        // payment - see LoanService.reduceForReturn); only the portion
        // that was actually collected as cash/bank is a real PAYMENT_OUT.
        // Without this split, a return against an unpaid credit sale would
        // both leave the Loan balance untouched (customer still shown as
        // owing for goods they returned) AND post a fictitious cash
        // refund for money that was never collected in the first place.
        BigDecimal loanReduction = BigDecimal.ZERO;
        if (sale.getRemainingAmount() != null && sale.getRemainingAmount().signum() > 0) {
            Loan loan = loanRepository.findBySaleId(sale.getId())
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "Sale " + sale.getSaleNumber() + " has an outstanding balance but no loan record"));
            loanReduction = loanService.reduceForReturn(loan, totalRefund);
        }
        BigDecimal cashRefund = totalRefund.subtract(loanReduction);

        LocalDate today = LocalDate.now();
        if (totalRefund.signum() > 0) {
            financialTransactionService.record(sale.getBranch(), FinancialTransactionType.SALE_REVENUE,
                    totalRefund.negate(), today, FinancialReferenceType.CUSTOMER_RETURN, saved.getId());
        }
        if (cashRefund.signum() > 0) {
            financialTransactionService.record(sale.getBranch(), FinancialTransactionType.PAYMENT_OUT,
                    cashRefund, today, FinancialReferenceType.CUSTOMER_RETURN, saved.getId());
        }
        if (totalReversedCogs.signum() > 0) {
            financialTransactionService.record(sale.getBranch(), FinancialTransactionType.COGS,
                    totalReversedCogs.negate(), today, FinancialReferenceType.CUSTOMER_RETURN, saved.getId());
        }

        return CustomerReturnResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerReturnSummaryResponse> search(BranchAccessService.BranchScope scope,
                                                                UUID specificBranchId, UUID saleId,
                                                                UUID customerId, java.time.Instant from, java.time.Instant to, String search,
                                                                Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = customerReturnRepository.search(branchIds, saleId, customerId, from, to, search, pageable)
                .map(CustomerReturnSummaryResponse::from);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public CustomerReturnResponse get(UUID id) {
        CustomerReturn customerReturn = customerReturnRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer return", id));
        branchAccessService.resolveReadableBranchId(customerReturn.getBranch().getId());
        return CustomerReturnResponse.from(customerReturn);
    }

    private SaleBatchAllocation findAllocationOnSale(Sale sale, UUID allocationId) {
        return sale.getItems().stream()
                .flatMap(item -> item.getAllocations().stream())
                .filter(allocation -> allocation.getId().equals(allocationId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException(
                        "Allocation " + allocationId + " does not belong to sale " + sale.getSaleNumber()));
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }
}
