package com.company.erp.loan;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.customer.Customer;
import com.company.erp.sales.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A credit balance owed by a Customer, created from exactly one Sale (spec
 * section 15 / ERD). Only ever created by {@code LoanService.createFromSale},
 * called from {@code PosCheckoutService} in the same transaction as the sale
 * itself, whenever that sale's remainingAmount > 0 - never created any other
 * way, so every Loan traces back to a real unpaid balance on a real sale.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "loans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_sale", columnNames = "sale_id")
})
public class Loan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingAmount;

    /** Only ever OPEN / PARTIALLY_PAID / PAID - see {@link LoanStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
}
