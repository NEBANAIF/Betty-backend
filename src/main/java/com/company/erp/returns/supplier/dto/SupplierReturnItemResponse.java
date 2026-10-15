package com.company.erp.returns.supplier.dto;

import com.company.erp.returns.supplier.SupplierReturnItem;

import java.util.List;
import java.util.UUID;

public record SupplierReturnItemResponse(
        UUID id,
        UUID purchaseOrderItemId,
        UUID productId,
        String productName,
        int quantity,
        List<SupplierReturnAllocationResponse> allocations
) {
    public static SupplierReturnItemResponse from(SupplierReturnItem item) {
        return new SupplierReturnItemResponse(
                item.getId(), item.getPurchaseOrderItem().getId(),
                item.getPurchaseOrderItem().getProduct().getId(),
                item.getPurchaseOrderItem().getProduct().getName(),
                item.getQuantity(),
                item.getAllocations().stream().map(SupplierReturnAllocationResponse::from).toList());
    }
}
