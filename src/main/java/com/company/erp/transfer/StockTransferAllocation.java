package com.company.erp.transfer;

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
 * "N units of this transfer item came out of sourceBatch at cost Y" -
 * written once, at the moment the transfer moves to IN_TRANSIT (i.e. once
 * FIFO consumption at the source branch actually happens). This is what
 * lets StockTransferService.receive() create a destination batch per line
 * at the EXACT preserved cost (spec section 19: "If 50 units are
 * transferred from a batch costing 110, destination receives 50 units,
 * cost 110") - never recalculated, never averaged, even if the requested
 * quantity spanned multiple source batches at different costs.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_transfer_allocations")
public class StockTransferAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_item_id", nullable = false)
    private StockTransferItem transferItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private ProductBatch sourceBatch;

    @Column(name = "quantity_allocated", nullable = false)
    private Integer quantityAllocated;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;
}
