package com.company.erp.report.dto;

import com.company.erp.finance.dto.BranchPnLResponse;

import java.math.BigDecimal;
import java.util.UUID;

/** One branch's P&L, for the branch comparison / "sales by branch" report (spec section 27). */
public record BranchComparisonRow(
        UUID branchId,
        String branchName,
        String branchType,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal totalExpenses,
        BigDecimal netProfit,
        BigDecimal profitMarginPercent
) {
    public static BranchComparisonRow of(UUID branchId, String branchName, String branchType, BranchPnLResponse pnl) {
        return new BranchComparisonRow(branchId, branchName, branchType, pnl.revenue(), pnl.cogs(),
                pnl.grossProfit(), pnl.totalExpenses(), pnl.netProfit(), pnl.profitMarginPercent());
    }
}
