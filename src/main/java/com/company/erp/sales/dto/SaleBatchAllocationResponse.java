package com.company.erp.sales.dto;

import com.company.erp.sales.SaleBatchAllocation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * id is the SaleBatchAllocation's own primary key - needed so a Customer
 * Return can reference the EXACT allocation line it's reversing (see
 * CustomerReturnItem's javadoc: returns are validated per-allocation, not
 * per-batch, since a product can appear in multiple allocations across
 * different sales or even different lines of the same sale).
 */
public record SaleBatchAllocationResponse(
        UUID id,
        UUID batchId,
        String batchNumber,
        int quantityAllocated,
        BigDecimal unitCost,
        BigDecimal lineCost
) {
    public SaleBatchAllocationResponse withoutCost() {
        return new SaleBatchAllocationResponse(id, batchId, batchNumber, quantityAllocated, null, null);
    }

    public static SaleBatchAllocationResponse from(SaleBatchAllocation a) {
        return new SaleBatchAllocationResponse(
                a.getId(), a.getBatch().getId(), a.getBatch().getBatchNumber(),
                a.getQuantityAllocated(), a.getUnitCost(), a.getLineCost());
    }
}
