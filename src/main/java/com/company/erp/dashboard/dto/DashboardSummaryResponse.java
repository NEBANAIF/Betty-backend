package com.company.erp.dashboard.dto;

import com.company.erp.expense.dto.ExpenseResponse;
import com.company.erp.report.dto.BranchComparisonRow;
import com.company.erp.sales.dto.SaleSummaryResponse;
import com.company.erp.transfer.dto.TransferSummaryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deliberately compact (spec section 26: "KPIs should be compact, elegant
 * and professional. Do not make giant KPI cards.") - one call gives the
 * frontend everything the dashboard's first screen needs: the KPI strip,
 * recent-activity lists, low-stock alerts, and branch performance. Trend
 * charts and deeper analytics live in ReportController as separate calls,
 * since a dashboard widget the user hasn't scrolled to shouldn't block the
 * KPIs above the fold.
 */
public record DashboardSummaryResponse(
        LocalDate from,
        LocalDate to,
        long totalSalesCount,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal totalExpenses,
        BigDecimal netProfit,
        BigDecimal profitMarginPercent,
        BigDecimal inventoryValue,
        long lowStockCount,
        long outOfStockCount,
        long inStockCount,
        List<SaleSummaryResponse> recentSales,
        List<TransferSummaryResponse> recentTransfers,
        List<ExpenseResponse> recentExpenses,
        List<LowStockAlertResponse> lowStockAlerts,
        List<BranchComparisonRow> branchPerformance
) {
}
