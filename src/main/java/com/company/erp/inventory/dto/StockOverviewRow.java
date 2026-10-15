package com.company.erp.inventory.dto;

import java.util.Map;
import java.util.UUID;

/**
 * One row of the Stock Overview tab: a product and its quantity at every
 * branch the caller is allowed to see, plus the total across those
 * branches. quantityByBranch is keyed by branchId - the frontend zips it
 * against StockOverviewResponse.branches to render the
 * "Product | Main Warehouse | Bole Store | ... | Total" table from spec
 * section 10.
 */
public record StockOverviewRow(
        UUID productId,
        String productName,
        String sku,
        Map<UUID, Integer> quantityByBranch,
        int total
) {
}
