package com.company.erp.inventory.dto;

/** How many active products fall in each stock bucket for the selected branches. total = the three added up. */
public record StockSummaryResponse(long total, long inStock, long lowStock, long outOfStock) {
}
