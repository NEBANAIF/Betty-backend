package com.company.erp.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cost and stock-value figures for every product that currently has stock
 * in the requested branch scope, plus the grand total. Products with no
 * stock have no row.
 */
public record ProductCostsResponse(
        BigDecimal totalStockValue,
        List<ProductCostRow> rows
) {
    /**
     * The same figures with what the caller may not see left out: batch costs need the
     * cost-price permission, stock value and the total need the inventory-value permission.
     * Quantities are always kept.
     */
    public ProductCostsResponse limitedTo(boolean showCosts, boolean showValue) {
        return new ProductCostsResponse(
                showValue ? totalStockValue : null,
                rows.stream().map(r -> new ProductCostRow(
                        r.productId(), r.quantityOnHand(),
                        showCosts ? r.oldestCost() : null,
                        showCosts ? r.latestCost() : null,
                        showValue ? r.stockValue() : null)).toList());
    }
}
