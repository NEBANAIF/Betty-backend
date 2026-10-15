package com.company.erp.sales;

import com.company.erp.batch.ProductBatch;
import com.company.erp.common.audit.BaseEntity;
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

/**
 * "N units of this sale item came out of batch X at cost Y" - the
 * permanent record of FIFO cost lineage for a sale (spec section 12).
 * COGS for a sale item is ALWAYS SUM(quantityAllocated * unitCost) over
 * these rows, never product.sellingPrice or any current price.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sale_batch_allocations")
public class SaleBatchAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @Column(name = "quantity_allocated", nullable = false)
    private Integer quantityAllocated;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    public BigDecimal getLineCost() {
        return unitCost.multiply(BigDecimal.valueOf(quantityAllocated));
    }
}
