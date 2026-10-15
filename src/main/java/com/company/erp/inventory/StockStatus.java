package com.company.erp.inventory;

/**
 * Where a product stands on stock, judged on what can actually be sold
 * (quantity minus reserved) against the product's own reorder level. These
 * are the same three buckets the dashboard counts, so the numbers agree:
 * <ul>
 *   <li>OUT_OF_STOCK - nothing available (0 or less)</li>
 *   <li>LOW_STOCK    - something available, but at or below the reorder level</li>
 *   <li>IN_STOCK     - available and above the reorder level</li>
 * </ul>
 * A product whose reorder level is 0 therefore goes straight from in stock to out of stock.
 */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus of(int available, int reorderLevel) {
        if (available <= 0) {
            return OUT_OF_STOCK;
        }
        return available <= reorderLevel ? LOW_STOCK : IN_STOCK;
    }
}
