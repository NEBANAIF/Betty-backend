package com.company.erp.finance;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
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
import java.util.UUID;

/**
 * The immutable financial ledger (spec section 24: "All financial
 * transactions must preserve branch, date, reference, amount, type").
 * Written once, by FinancialTransactionService.record, at the exact
 * moment a real financial event happens - a sale, an expense, a loan
 * payment, a customer return - never edited or deleted afterward.
 * <p>
 * This plays exactly the role for money that StockHistory plays for
 * stock: an append-only audit trail, used for drill-down/reporting. It is
 * deliberately NOT what FinanceReportService sums to produce the branch
 * P&amp;L - just as current stock level is always read from
 * Inventory/ProductBatch and never recomputed by replaying StockHistory,
 * the P&amp;L is always computed directly from Sale/SaleBatchAllocation/
 * CustomerReturn/Expense (the actual source of truth), never from this
 * ledger. See FinanceReportService for the full reasoning.
 * <p>
 * amount may be negative (a customer return posts negative SALE_REVENUE
 * and, if restocked, negative COGS) - the ledger records the reversal as
 * its own signed entry rather than mutating or deleting the original one.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinancialTransactionType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private FinancialReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;
}
