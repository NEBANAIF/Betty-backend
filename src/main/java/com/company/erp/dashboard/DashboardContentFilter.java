package com.company.erp.dashboard;

import com.company.erp.dashboard.dto.DashboardSummaryResponse;
import com.company.erp.report.dto.BranchComparisonRow;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Removes from the dashboard response everything the caller's role is not allowed
 * to see, so a hidden figure never reaches the browser at all. Numbers become
 * null (or 0 for the two counts, lists become empty); the screen also hides the
 * matching cards, using the same permissions.
 */
@Component
@RequiredArgsConstructor
public class DashboardContentFilter {

    private final ContentAccess access;

    public DashboardSummaryResponse apply(DashboardSummaryResponse s) {
        boolean revenue = access.can(Permission.DASHBOARD_REVENUE);
        boolean profit = access.can(Permission.VIEW_PROFIT);
        boolean expenses = access.can(Permission.DASHBOARD_EXPENSES);
        boolean inventoryValue = access.can(Permission.VIEW_INVENTORY_VALUE);
        boolean lowStock = access.can(Permission.DASHBOARD_LOW_STOCK);

        List<BranchComparisonRow> branches = access.can(Permission.DASHBOARD_BRANCH_PERFORMANCE)
                ? s.branchPerformance().stream().map(row -> new BranchComparisonRow(
                        row.branchId(), row.branchName(), row.branchType(),
                        revenue ? row.revenue() : null,
                        profit ? row.cogs() : null,
                        profit ? row.grossProfit() : null,
                        expenses ? row.totalExpenses() : null,
                        profit ? row.netProfit() : null,
                        profit ? row.profitMarginPercent() : null)).toList()
                : List.of();

        return new DashboardSummaryResponse(
                s.from(), s.to(),
                revenue ? s.totalSalesCount() : 0,
                revenue ? s.revenue() : null,
                profit ? s.cogs() : null,
                profit ? s.grossProfit() : null,
                expenses ? s.totalExpenses() : null,
                profit ? s.netProfit() : null,
                profit ? s.profitMarginPercent() : null,
                inventoryValue ? s.inventoryValue() : null,
                lowStock ? s.lowStockCount() : 0,
                lowStock ? s.outOfStockCount() : 0,
                lowStock ? s.inStockCount() : 0,
                access.can(Permission.DASHBOARD_RECENT_SALES) ? s.recentSales() : List.of(),
                access.can(Permission.DASHBOARD_RECENT_TRANSFERS) ? s.recentTransfers() : List.of(),
                expenses ? s.recentExpenses() : List.of(),
                lowStock ? s.lowStockAlerts() : List.of(),
                branches);
    }
}
