package com.company.erp.inventory.dto;

import com.company.erp.inventory.Inventory;

import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        String productName,
        UUID branchId,
        String branchName,
        int quantity,
        int reservedQuantity,
        int availableQuantity
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProduct().getId(),
                inventory.getProduct().getName(),
                inventory.getBranch().getId(),
                inventory.getBranch().getName(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity()
        );
    }
}
