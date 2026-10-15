package com.company.erp.transfer.dto;

import com.company.erp.transfer.StockTransferAllocation;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferAllocationResponse(
        UUID sourceBatchId,
        String sourceBatchNumber,
        int quantityAllocated,
        BigDecimal unitCost
) {
    public static TransferAllocationResponse from(StockTransferAllocation a) {
        return new TransferAllocationResponse(
                a.getSourceBatch().getId(), a.getSourceBatch().getBatchNumber(),
                a.getQuantityAllocated(), a.getUnitCost());
    }
}
