package com.company.erp.inventory.dto;

import java.util.List;

/**
 * The Reports page's stock-status section: the counts (for the chart and the plain-language
 * sentence) and the products that need restocking, out-of-stock first, then lowest first.
 * needsAttention is capped, so compare its size with summary.lowStock + summary.outOfStock
 * to know whether the list was cut short.
 */
public record StockStatusReportResponse(StockSummaryResponse summary, List<StockAttentionRow> needsAttention) {
}
