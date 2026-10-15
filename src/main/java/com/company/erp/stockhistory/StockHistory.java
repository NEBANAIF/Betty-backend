package com.company.erp.stockhistory;

import com.company.erp.batch.ProductBatch;
import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.product.Product;
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

import java.time.Instant;
import java.util.UUID;

/**
 * The complete inventory ledger (spec section 20). Every inventory
 * movement, no exceptions, writes exactly one row here in the same
 * transaction as the movement itself. Rows are never updated or deleted -
 * this is an append-only audit trail, which is why it extends BaseEntity
 * but its `updatedAt`/`version` fields are simply never touched again
 * after creation.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_history")
public class StockHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Null only for movements that don't have batch granularity (should be rare/never in practice). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private StockMovementType movementType;

    /** Positive for additions, negative for deductions - the UI colors this green/red directly off the sign. */
    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private int newQuantity;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private StockReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
