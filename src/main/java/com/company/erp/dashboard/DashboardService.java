package com.company.erp.dashboard;

import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.dashboard.dto.DashboardSummaryResponse;
import com.company.erp.dashboard.dto.LowStockAlertResponse;
import com.company.erp.expense.ExpenseRepository;
import com.company.erp.expense.dto.ExpenseResponse;
import com.company.erp.finance.FinanceReportService;
import com.company.erp.inventory.InventoryRepository;
import com.company.erp.report.ReportService;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.SaleStatus;
import com.company.erp.sales.dto.SaleSummaryResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.transfer.StockTransferRepository;
import com.company.erp.transfer.dto.TransferSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Backs the dashboard's first screen (spec section 26) in one call: the
 * compact KPI strip, a handful of recent-activity rows per module, low
 * stock alerts, and branch performance. Everything here is read directly
 * from source tables / FinanceReportService / ReportService - nothing is
 * cached or precomputed, so the numbers are always exactly as current as
 * the database.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final int LOW_STOCK_ALERT_LIMIT = 20;

    private final FinanceReportService financeReportService;
    private final ReportService reportService;
    private final BranchAccessService branchAccessService;
    private final SaleRepository saleRepository;
    private final StockTransferRepository stockTransferRepository;
    private final ExpenseRepository expenseRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductBatchRepository productBatchRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                LocalDate from, LocalDate to) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);

        var pnl = financeReportService.getPnL(scope, specificBranchId, from, to);

        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
        long totalSalesCount = saleRepository.search(branchIds, null, null, SaleStatus.COMPLETED,
                fromInstant, toInstant, null, PageRequest.of(0, 1)).getTotalElements();

        // Inventory value/stock levels are a CURRENT snapshot, not scoped
        // to [from, to] - "how much stock is on hand right now" doesn't
        // have a date range, unlike the P&L figures above it.
        var inventoryValue = productBatchRepository.sumInventoryValueForBranches(branchIds);
        long lowStockCount = inventoryRepository.countLowStock(branchIds);
        long outOfStockCount = inventoryRepository.countOutOfStock(branchIds);
        long inStockCount = inventoryRepository.countInStock(branchIds);
        List<LowStockAlertResponse> lowStockAlerts = inventoryRepository
                .findLowStockAlerts(branchIds, PageRequest.of(0, LOW_STOCK_ALERT_LIMIT))
                .stream().map(LowStockAlertResponse::from).toList();

        List<SaleSummaryResponse> recentSales = saleRepository
                .search(branchIds, null, null, null, null, null, null, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .map(SaleSummaryResponse::from).getContent();
        List<TransferSummaryResponse> recentTransfers = stockTransferRepository
                .search(branchIds, null, null, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .map(TransferSummaryResponse::from).getContent();
        List<ExpenseResponse> recentExpenses = expenseRepository
                .search(branchIds, null, null, null, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .map(ExpenseResponse::from).getContent();

        var branchPerformance = reportService.getBranchComparison(scope, specificBranchId, from, to);

        return new DashboardSummaryResponse(from, to, totalSalesCount, pnl.revenue(), pnl.cogs(), pnl.grossProfit(),
                pnl.totalExpenses(), pnl.netProfit(), pnl.profitMarginPercent(), inventoryValue,
                lowStockCount, outOfStockCount, inStockCount, recentSales, recentTransfers, recentExpenses,
                lowStockAlerts, branchPerformance);
    }
}
