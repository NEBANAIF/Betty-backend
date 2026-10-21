package com.company.erp.debt;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.purchase.PurchaseOrder;
import com.company.erp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money the BUSINESS owes someone else - a bank loan, supplier credit, money borrowed from a person.
 * (Customer credit - what customers owe us - is the separate Loan entity.) A debt belongs to the whole
 * business, not to a branch. totalAmount = principal + interestAmount is what has to be paid back.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "debts")
public class Debt extends BaseEntity {

    @Column(name = "lender_name", nullable = false, length = 200)
    private String lenderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", nullable = false, length = 20)
    private DebtType debtType;

    @Column(length = 500)
    private String description;

    @Column(name = "principal_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal principalAmount;

    /** Extra to pay back on top of the principal (interest / fees). 0 if none. */
    @Column(name = "interest_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebtStatus status = DebtStatus.OPEN;

    @Column(length = 1000)
    private String notes;

    /** Set when this credit was created automatically by a purchase order bought on credit; null otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
