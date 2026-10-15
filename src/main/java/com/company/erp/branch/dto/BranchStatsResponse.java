package com.company.erp.branch.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Branch statistics shown on the Branch Management detail view (spec
 * section 28). Populated by BranchStatsService once Inventory (Phase 4),
 * Sales (Phase 6), Purchases (Phase 5), and Finance (Phase 10) exist -
 * wired here now as the shape callers can already depend on, with the
 * service returning zeros until those phases land.
 */
public record BranchStatsResponse(
        UUID branchId,
        long totalStockUnits,
        BigDecimal inventoryValue,
        long totalSalesCount,
        BigDecimal totalRevenue,
        long totalPurchasesCount,
        BigDecimal totalExpenses,
        BigDecimal grossProfit
) {
    public static BranchStatsResponse zero(UUID branchId) {
        return new BranchStatsResponse(branchId, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO,
                0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
