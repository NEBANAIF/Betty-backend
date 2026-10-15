package com.company.erp.batch;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A single stock receipt: N units of a product, at a branch, at a fixed
 * cost price, on a given date. This is the SOURCE OF TRUTH for stock and
 * cost - Inventory is just a cached aggregate kept in sync with batches by
 * the same transaction that mutates them.
 *
 * Deliberately does NOT have: sellingPrice, barcode. Different batches of
 * the same product may have different cost prices, and that difference
 * must be preserved through sales (FIFO COGS), transfers (destination
 * inherits source batch's cost), and returns (restock back into the
 * originating batch's cost) - never recalculated from the product's
 * current selling price.
 *
 * expiryDate is optional (nullable) - most products never set it, but
 * perishable/medical goods (e.g. cosmetics with a shelf life) can record
 * when this specific batch expires.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_batches")
public class ProductBatch extends BaseEntity {

    @Column(name = "batch_number", nullable = false, unique = true)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "cost_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    /**
     * Optional. When this batch expires, e.g. cosmetics with a shelf life.
     * Null means "no expiry tracked for this batch".
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * The PurchaseOrderItem that caused this batch to be created via
     * purchase receiving, if any (null for batches created by a manual
     * stock-increase adjustment). Plain UUID column, no JPA relation, so
     * purchase.* stays a one-way dependency on batch.* rather than a
     * cycle. Added specifically so Supplier Returns can remove stock from
     * the EXACT batch a given purchase created (see
     * FifoConsumptionService.consumeFromPurchaseOrderItem) instead of
     * FIFO-guessing across every batch of that product at the branch,
     * which could otherwise attribute cost/value to the wrong supplier
     * when multiple suppliers ship the same product to the same branch.
     */
    @Column(name = "source_purchase_order_item_id")
    private java.util.UUID sourcePurchaseOrderItemId;

    public boolean isDepleted() {
        return remainingQuantity <= 0;
    }
}
