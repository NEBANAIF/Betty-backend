package com.company.erp.dashboard.dto;

import com.company.erp.inventory.Inventory;

import java.util.UUID;

public record LowStockAlertResponse(
        UUID productId,
        String productName,
        String sku,
        UUID branchId,
        String branchName,
        int availableQuantity,
        int reorderLevel
) {
    public static LowStockAlertResponse from(Inventory inventory) {
        return new LowStockAlertResponse(
                inventory.getProduct().getId(), inventory.getProduct().getName(), inventory.getProduct().getSku(),
                inventory.getBranch().getId(), inventory.getBranch().getName(),
                inventory.getAvailableQuantity(), inventory.getProduct().getReorderLevel());
    }
}
