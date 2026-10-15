package com.company.erp.returns.supplier;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.purchase.PurchaseOrderItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * "select purchase, select items" (spec 22) - purchaseOrderItem is the
 * line being returned against; quantity is capped at what that line
 * actually received minus whatever's already been returned against it
 * (SupplierReturnItemRepository.sumReturnedForPurchaseOrderItem).
 * <p>
 * Removal is resolved via StockMutationService.issueFromPurchaseOrderItem,
 * which FIFO-consumes only among the batch(es)
 * ProductBatch.sourcePurchaseOrderItemId ties back to this exact line -
 * never a generic FIFO sweep across every batch of the product at the
 * branch, which could otherwise draw from an unrelated purchase or a
 * different supplier's shipment of the same product. allocations records
 * exactly which batch(es) and cost(s) were actually touched.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier_return_items")
public class SupplierReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_return_id", nullable = false)
    private SupplierReturn supplierReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(nullable = false)
    private Integer quantity;

    @OneToMany(mappedBy = "supplierReturnItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SupplierReturnAllocation> allocations = new ArrayList<>();

    public void addAllocation(SupplierReturnAllocation allocation) {
        allocation.setSupplierReturnItem(this);
        allocations.add(allocation);
    }
}
