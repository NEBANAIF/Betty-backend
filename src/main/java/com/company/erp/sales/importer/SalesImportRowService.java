package com.company.erp.sales.importer;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.customer.Customer;
import com.company.erp.customer.CustomerRepository;
import com.company.erp.payment.PaymentMethod;
import com.company.erp.pos.PosCheckoutService;
import com.company.erp.pos.dto.CheckoutItemRequest;
import com.company.erp.pos.dto.CheckoutRequest;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.dto.SaleResponse;
import com.company.erp.sales.importer.dto.SalesImportLine;
import com.company.erp.sales.importer.dto.SalesImportSale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Records ONE imported sale. A separate bean from {@link SalesImportService} so its {@code @Transactional}
 * really applies: if anything fails (unknown product, not enough stock...) the transaction rolls back and
 * nothing of this sale - stock, payment, loan, a customer created for it - is kept.
 * <p>
 * The sale itself goes through the same {@link PosCheckoutService} as a till sale, so stock (oldest batch first),
 * cost of goods, payments, loans and the finance ledger are identical to a sale rung up by hand.
 */
@Service
@RequiredArgsConstructor
public class SalesImportRowService {

    private static final int DEFAULT_LOAN_DAYS = 30;

    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final PosCheckoutService posCheckoutService;

    @Transactional
    public String importSale(SalesImportSale sale, UUID defaultBranchId, UUID bankId) {
        if (sale.error() != null) {
            throw new BusinessRuleViolationException(sale.error());
        }
        for (SalesImportLine line : sale.lines()) {
            if (line.error() != null) {
                throw new BusinessRuleViolationException("Row " + line.rowNumber() + ": " + line.error());
            }
        }
        if (sale.date() == null) {
            throw new BusinessRuleViolationException("The date is missing");
        }
        if (sale.date().isAfter(LocalDate.now())) {
            throw new BusinessRuleViolationException("The date cannot be in the future");
        }

        String saleNumber = sale.invoiceNo();
        if (saleNumber != null) {
            if (saleRepository.existsBySaleNumber(saleNumber)) {
                throw new BusinessRuleViolationException(
                        "Invoice No '" + saleNumber + "' already exists - nothing was recorded for it");
            }
        } else {
            saleNumber = newSaleNumber(sale.date());
        }

        Branch branch = resolveBranch(sale.branch(), defaultBranchId);

        List<CheckoutItemRequest> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SalesImportLine line : sale.lines()) {
            if (line.product() == null || line.product().isBlank()) {
                throw new BusinessRuleViolationException("Row " + line.rowNumber() + ": Product is missing");
            }
            if (line.quantity() == null) {
                throw new BusinessRuleViolationException("Row " + line.rowNumber() + ": Quantity must be a whole number above 0");
            }
            if (line.unitPrice() == null) {
                throw new BusinessRuleViolationException("Row " + line.rowNumber() + ": Unit Price must be a number, 0 or more");
            }
            Product product = resolveProduct(line.product());
            items.add(new CheckoutItemRequest(product.getId(), line.quantity(), line.unitPrice(), null));
            subtotal = subtotal.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }

        BigDecimal discount = sale.discount() != null ? sale.discount() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);
        // Blank Amount Paid means paid in full; 0 means a sale on credit.
        BigDecimal paid = sale.amountPaid() != null ? sale.amountPaid() : total;

        PaymentMethod method = resolveMethod(sale.paymentMethod());
        if (method == PaymentMethod.BANK && paid.signum() > 0 && bankId == null) {
            throw new BusinessRuleViolationException("Choose the bank used for Bank payments on the import screen");
        }

        Customer customer = null;
        if (sale.customer() != null) {
            customer = findOrCreateCustomer(sale.customer());
        }

        CheckoutRequest request = new CheckoutRequest(
                branch.getId(),
                customer != null ? customer.getId() : null,
                items,
                discount,
                method,
                paid,
                null,
                null,
                sale.date().plusDays(DEFAULT_LOAN_DAYS),
                method == PaymentMethod.BANK ? bankId : null);

        SaleResponse created = posCheckoutService.checkout(request,
                PosCheckoutService.SaleContext.imported(saleNumber, sale.date()));
        return saleNumber + " · " + items.size() + (items.size() == 1 ? " line" : " lines")
                + " · total " + total.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + (created != null && paid.compareTo(total) < 0
                ? " · unpaid " + total.subtract(paid).setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "");
    }

    // ------------------------------------------------------------------ lookups

    private Branch resolveBranch(String text, UUID defaultBranchId) {
        if (text == null) {
            if (defaultBranchId == null) {
                throw new BusinessRuleViolationException(
                        "Branch is missing - write it in the Branch column or choose one on the import screen");
            }
            return branchRepository.findById(defaultBranchId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Branch", defaultBranchId));
        }
        String name = collapse(text);
        return branchRepository.findByCodeIgnoreCase(name)
                .or(() -> branchRepository.findByNameIgnoreCase(name))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Branch '" + text + "' doesn't exist - check the name/code against Branches"));
    }

    private Product resolveProduct(String text) {
        String trimmed = text.trim();
        String collapsed = collapse(trimmed);
        return productRepository.findBySkuIgnoreCase(trimmed)
                .or(() -> productRepository.findFirstByNameIgnoreCase(trimmed))
                .or(() -> productRepository.findFirstByNameIgnoreCase(collapsed))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Product '" + text + "' not found - it must already exist under Inventory > Products"));
    }

    private Customer findOrCreateCustomer(String text) {
        String name = collapse(text);
        return customerRepository.findFirstByNameIgnoreCase(name).orElseGet(() -> {
            Customer c = new Customer();
            c.setName(name);
            c.setActive(true);
            return customerRepository.save(c);
        });
    }

    private PaymentMethod resolveMethod(String text) {
        if (text == null) {
            return PaymentMethod.CASH;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "cash" -> PaymentMethod.CASH;
            case "bank" -> PaymentMethod.BANK;
            default -> throw new BusinessRuleViolationException("Payment Method must be Cash or Bank (or left blank for Cash)");
        };
    }

    private String newSaleNumber(LocalDate date) {
        String stamp = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            candidate = "SALE-" + stamp + "-" + ThreadLocalRandom.current().nextInt(10000, 99999);
        } while (saleRepository.existsBySaleNumber(candidate));
        return candidate;
    }

    private static String collapse(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }
}
