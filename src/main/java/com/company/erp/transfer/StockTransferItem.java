package com.company.erp.transfer;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.product.Product;
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
 * quantity is what was REQUESTED, fixed at creation. Which batches (and
 * therefore costs) actually fulfilled it is only known once the transfer
 * ships (status -> IN_TRANSIT) - that's recorded in `allocations`, empty
 * until then. This mirrors SaleItem/SaleBatchAllocation deliberately: a
 * transfer's source-side deduction is FIFO consumption exactly like a
 * sale, just issued to another branch instead of to a customer.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @OneToMany(mappedBy = "transferItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockTransferAllocation> allocations = new ArrayList<>();

    public void addAllocation(StockTransferAllocation allocation) {
        allocation.setTransferItem(this);
        allocations.add(allocation);
    }
}
