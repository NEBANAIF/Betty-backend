package com.company.erp.returns.supplier.dto;

import com.company.erp.returns.supplier.SupplierReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierReturnResponse(
        UUID id,
        UUID purchaseOrderId,
        String purchaseOrderNumber,
        UUID supplierId,
        String supplierName,
        UUID branchId,
        String branchName,
        String status,
        String processedByName,
        BigDecimal totalValue,
        Instant createdAt,
        List<SupplierReturnItemResponse> items
) {
    public static SupplierReturnResponse from(SupplierReturn r) {
        var itemResponses = r.getItems().stream().map(SupplierReturnItemResponse::from).toList();
        BigDecimal totalValue = itemResponses.stream()
                .flatMap(i -> i.allocations().stream())
                .map(SupplierReturnAllocationResponse::lineValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SupplierReturnResponse(
                r.getId(), r.getPurchaseOrder().getId(), r.getPurchaseOrder().getOrderNumber(),
                r.getSupplier().getId(), r.getSupplier().getName(),
                r.getBranch().getId(), r.getBranch().getName(),
                r.getStatus().name(), r.getProcessedBy().getName(), totalValue,
                r.getCreatedAt(), itemResponses);
    }
}
