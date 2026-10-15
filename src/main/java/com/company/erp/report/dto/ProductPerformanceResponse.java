package com.company.erp.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of "sales by product" / best-selling / slow-moving (spec sections 26/27 - all three read the same underlying aggregate, just sorted or filtered differently). */
public record ProductPerformanceResponse(
        UUID productId,
        String productName,
        String sku,
        long quantitySold,
        BigDecimal revenue
) {
}
