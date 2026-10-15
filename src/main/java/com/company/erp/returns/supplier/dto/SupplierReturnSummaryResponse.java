package com.company.erp.returns.supplier.dto;

import com.company.erp.returns.supplier.SupplierReturn;

import java.time.Instant;
import java.util.UUID;

public record SupplierReturnSummaryResponse(
        UUID id,
        String purchaseOrderNumber,
        String supplierName,
        String branchName,
        String status,
        Instant createdAt
) {
    public static SupplierReturnSummaryResponse from(SupplierReturn r) {
        return new SupplierReturnSummaryResponse(
                r.getId(), r.getPurchaseOrder().getOrderNumber(), r.getSupplier().getName(),
                r.getBranch().getName(), r.getStatus().name(), r.getCreatedAt());
    }
}
