package com.company.erp.inventory;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Branch-scoped stock aggregate for one product. This is a CACHE, not the
 * source of truth - ProductBatch.remainingQuantity is. Every write to this
 * table happens in the same transaction as the corresponding batch
 * mutation (see StockMutationService), so the two never drift; a
 * reconciliation job (StockConsistencyService, added alongside reporting)
 * can re-derive `quantity` from SUM(batch.remainingQuantity) at any time
 * as a integrity check.
 *
 * availableQuantity = quantity - reservedQuantity is computed, never
 * stored (see InventoryResponse).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_product_branch", columnNames = {"product_id", "branch_id"})
})
public class Inventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private int quantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }
}
