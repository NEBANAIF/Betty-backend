package com.company.erp.returns.supplier;

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
 * "N units of this return line actually came out of batch X at cost Y" -
 * written once, at creation, from what FifoConsumptionService.consume
 * returns. This is the exact value credited back against the supplier's
 * balance (see SupplierReturnAllocationRepository.sumReturnedValueForSupplier).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier_return_allocations")
public class SupplierReturnAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_return_item_id", nullable = false)
    private SupplierReturnItem supplierReturnItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @Column(name = "quantity_allocated", nullable = false)
    private Integer quantityAllocated;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;
}
