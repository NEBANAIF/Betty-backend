package com.company.erp.dashboard;

import com.company.erp.dashboard.dto.DashboardSummaryResponse;
import com.company.erp.report.dto.BranchComparisonRow;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardContentFilterTest {

    private DashboardSummaryResponse everything() {
        BranchComparisonRow row = new BranchComparisonRow(UUID.randomUUID(), "Bole Store", "STORE",
                bd(1000), bd(600), bd(400), bd(100), bd(300), bd(30));
        return new DashboardSummaryResponse(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20),
                7, bd(1000), bd(600), bd(400), bd(100), bd(300), bd(30), bd(5000),
                4, 2, 25, List.of(), List.of(), List.of(), List.of(), List.of(row));
    }

    private BigDecimal bd(int value) {
        return BigDecimal.valueOf(value);
    }

    private DashboardContentFilter filterFor(Set<Permission> allowed) {
        ContentAccess access = mock(ContentAccess.class);
        when(access.can(any(Permission.class))).thenAnswer(inv -> allowed.contains(inv.getArgument(0)));
        return new DashboardContentFilter(access);
    }

    @Test
    void aStoreManagerWithoutInventoryValueSeesEverythingElse() {
        Set<Permission> allowed = EnumSet.allOf(Permission.class);
        allowed.remove(Permission.VIEW_INVENTORY_VALUE);

        var result = filterFor(allowed).apply(everything());

        assertThat(result.inventoryValue()).isNull();
        assertThat(result.revenue()).isEqualByComparingTo("1000");
        assertThat(result.grossProfit()).isEqualByComparingTo("400");
        assertThat(result.totalSalesCount()).isEqualTo(7);
        assertThat(result.lowStockCount()).isEqualTo(4);
        assertThat(result.inStockCount()).isEqualTo(25);
        assertThat(result.branchPerformance()).hasSize(1);
    }

    @Test
    void aRoleWithOnlyDashboardViewGetsNoFiguresAtAll() {
        var result = filterFor(EnumSet.of(Permission.DASHBOARD_VIEW)).apply(everything());

        assertThat(result.revenue()).isNull();
        assertThat(result.cogs()).isNull();
        assertThat(result.grossProfit()).isNull();
        assertThat(result.netProfit()).isNull();
        assertThat(result.profitMarginPercent()).isNull();
        assertThat(result.totalExpenses()).isNull();
        assertThat(result.inventoryValue()).isNull();
        assertThat(result.totalSalesCount()).isZero();
        assertThat(result.lowStockCount()).isZero();
        assertThat(result.outOfStockCount()).isZero();
        assertThat(result.inStockCount()).isZero();
        assertThat(result.branchPerformance()).isEmpty();
    }

    @Test
    void theBranchTableHidesProfitColumnsWithoutTheProfitPermission() {
        var result = filterFor(EnumSet.of(Permission.DASHBOARD_VIEW, Permission.DASHBOARD_BRANCH_PERFORMANCE,
                Permission.DASHBOARD_REVENUE)).apply(everything());

        BranchComparisonRow row = result.branchPerformance().get(0);
        assertThat(row.branchName()).isEqualTo("Bole Store");
        assertThat(row.revenue()).isEqualByComparingTo("1000");
        assertThat(row.grossProfit()).isNull();
        assertThat(row.netProfit()).isNull();
        assertThat(row.profitMarginPercent()).isNull();
        assertThat(row.totalExpenses()).isNull();
    }
}
