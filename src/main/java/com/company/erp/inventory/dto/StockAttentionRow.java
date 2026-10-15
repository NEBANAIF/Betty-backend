package com.company.erp.inventory.dto;

import com.company.erp.inventory.StockStatus;

import java.util.UUID;

/** A product that is low or out of stock, with what is left and the level it is measured against. */
public record StockAttentionRow(
        UUID productId,
        String productName,
        String sku,
        String unit,
        int available,
        int reorderLevel,
        StockStatus status
) {
}
