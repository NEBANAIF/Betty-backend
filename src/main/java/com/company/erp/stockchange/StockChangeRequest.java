package com.company.erp.stockchange;

import com.company.erp.batch.ProductBatch;
import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.product.Product;
import com.company.erp.stockhistory.StockMovementType;
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
import java.time.Instant;
import java.time.LocalDate;

/**
 * Stock that someone added or removed BY HAND, held until it is checked. While it is PENDING the
 * stock has not moved at all; approving it is what moves the stock (through StockMutationService,
 * like every other stock change), rejecting it discards it. What was typed - product, branch,
 * quantity, cost, reason - is kept exactly as entered so the approver sees it before it counts.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_change_requests")
public class StockChangeRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockChangeType changeType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private int quantity;

    /** ADD_BATCH only: what one unit cost. */
    @Column(precision = 14, scale = 4)
    private BigDecimal costPrice;

    /** ADD_BATCH only: when the goods were received (FIFO orders batches by it). */
    private LocalDate receivedDate;

    /** ADD_BATCH only, optional: when this batch expires, if the product tracks expiry. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** REMOVE_STOCK only: ADJUSTMENT (correction), DAMAGED or LOST. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StockMovementType movementType;

    /** REMOVE_STOCK only, optional: take it from this exact batch instead of oldest-first. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    /** EDIT_BATCH only: the batch number to set (null = leave as is). costPrice / expiryDate hold the other proposed values. */
    @Column(name = "new_batch_number", length = 100)
    private String newBatchNumber;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockChangeStatus status = StockChangeStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private Instant reviewedAt;

    @Column(length = 500)
    private String reviewNote;
}
