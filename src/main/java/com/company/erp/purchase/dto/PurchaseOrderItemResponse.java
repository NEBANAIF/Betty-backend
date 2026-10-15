package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchaseOrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        int quantity,
        int receivedQuantity,
        int remainingToReceive,
        BigDecimal unitCost,
        BigDecimal lineTotal
) {
    public static PurchaseOrderItemResponse from(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getSku(),
                item.getQuantity(),
                item.getReceivedQuantity(),
                item.getRemainingToReceive(),
                item.getUnitCost(),
                item.getLineTotal()
        );
    }
}
