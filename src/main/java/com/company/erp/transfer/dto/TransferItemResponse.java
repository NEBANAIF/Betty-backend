package com.company.erp.transfer.dto;

import com.company.erp.transfer.StockTransferItem;

import java.util.List;
import java.util.UUID;

public record TransferItemResponse(
        UUID productId,
        String productName,
        String sku,
        int quantity,
        List<TransferAllocationResponse> allocations
) {
    public static TransferItemResponse from(StockTransferItem item) {
        return new TransferItemResponse(
                item.getProduct().getId(), item.getProduct().getName(), item.getProduct().getSku(),
                item.getQuantity(),
                item.getAllocations().stream().map(TransferAllocationResponse::from).toList());
    }
}
