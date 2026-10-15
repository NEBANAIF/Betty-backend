package com.company.erp.product.dto;

import com.company.erp.inventory.StockStatus;
import com.company.erp.product.Product;

import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        String description,
        String unit,
        Integer reorderLevel,
        boolean active,
        /** Available stock over the selected branches - only filled in by the product list, otherwise null. */
        Integer availableStock,
        /** IN_STOCK / LOW_STOCK / OUT_OF_STOCK for that stock - only filled in by the product list, otherwise null. */
        StockStatus stockStatus
) {
    public static ProductResponse from(Product product) {
        return from(product, null);
    }

    /** With the stock the product list shows; pass null for responses that carry no stock (create, update). */
    public static ProductResponse from(Product product, Integer availableStock) {
        Integer reorder = product.getReorderLevel();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getUnit(),
                product.getReorderLevel(),
                product.isActive(),
                availableStock,
                availableStock == null ? null : StockStatus.of(availableStock, reorder == null ? 0 : reorder)
        );
    }
}
