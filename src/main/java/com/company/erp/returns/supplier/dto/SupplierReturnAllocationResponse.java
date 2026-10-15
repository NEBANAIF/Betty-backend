package com.company.erp.returns.supplier.dto;

import com.company.erp.returns.supplier.SupplierReturnAllocation;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierReturnAllocationResponse(
        UUID batchId,
        String batchNumber,
        int quantityAllocated,
        BigDecimal unitCost,
        BigDecimal lineValue
) {
    public static SupplierReturnAllocationResponse from(SupplierReturnAllocation a) {
        return new SupplierReturnAllocationResponse(
                a.getBatch().getId(), a.getBatch().getBatchNumber(), a.getQuantityAllocated(),
                a.getUnitCost(), a.getUnitCost().multiply(BigDecimal.valueOf(a.getQuantityAllocated())));
    }
}
