package com.company.erp.report;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.finance.FinanceReportService;
import com.company.erp.report.dto.BranchComparisonRow;
import com.company.erp.report.dto.DailyFigureResponse;
import com.company.erp.report.dto.GrowthResponse;
import com.company.erp.report.dto.ProductPerformanceResponse;
import com.company.erp.sales.SaleBatchAllocationRepository;
import com.company.erp.sales.SaleItemRepository;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.SaleStatus;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The analytics layer of spec section 27. Deliberately does NOT duplicate
 * the many "X Report" list pages the spec names (Sales/Purchase/Inventory/
 * Expense/Stock Movement/Transfer/Return Reports) - each of those is just
 * the existing branch-scoped search endpoint on its own controller
 * (SalesHistoryController, PurchaseOrderController, StockHistoryController,
 * ExpenseController, StockTransferController, CustomerReturnController,
 * SupplierReturnController) with a date filter, all of which already
 * exist. What's genuinely new here is the aggregation/analytics that none
 * of those endpoints already provide: best-selling/slow-moving products,
 * sales by product, branch comparison, and growth trends.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleItemRepository saleItemRepository;
    private final SaleRepository saleRepository;
    private final SaleBatchAllocationRepository saleBatchAllocationRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessService branchAccessService;
    private final FinanceReportService financeReportService;

    /** Highest quantitySold first - spec's "Best-selling products". */
    @Transactional(readOnly = true)
    public List<ProductPerformanceResponse> getBestSellingProducts(BranchAccessService.BranchScope scope,
                                                                     UUID specificBranchId, LocalDate from,
                                                                     LocalDate to, int limit) {
        List<ProductPerformanceResponse> all = productPerformance(scope, specificBranchId, from, to);
        return all.stream().limit(limit).toList();
    }

    /**
     * Lowest quantitySold first, restricted to products that sold at
     * least once in the window (a product with literally zero sales
     * belongs in a "never sold" report, not "slow moving" - those are
     * different problems) - spec's "Slow-moving products".
     */
    @Transactional(readOnly = true)
    public List<ProductPerformanceResponse> getSlowMovingProducts(BranchAccessService.BranchScope scope,
                                                                    UUID specificBranchId, LocalDate from,
                                                                    LocalDate to, int limit) {
        List<ProductPerformanceResponse> all = productPerformance(scope, specificBranchId, from, to);
        return all.stream()
                .sorted((a, b) -> Long.compare(a.quantitySold(), b.quantitySold()))
                .limit(limit)
                .toList();
    }

    /** "Sales by product" (spec 27) is the same aggregate, unsorted-by-rank and uncapped by default. */
    @Transactional(readOnly = true)
    public List<ProductPerformanceResponse> getSalesByProduct(BranchAccessService.BranchScope scope,
                                                                UUID specificBranchId, LocalDate from, LocalDate to) {
        return productPerformance(scope, specificBranchId, from, to);
    }

    /** Day-bucketed revenue/COGS/gross-profit/sales-count - the Dashboard's sales/revenue/profit trend widgets. */
    @Transactional(readOnly = true)
    public List<DailyFigureResponse> getSalesTrend(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                     LocalDate from, LocalDate to) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        Instant[] range = toInstantRange(from, to);

        Map<LocalDate, BigDecimal[]> revenueAndCount = new LinkedHashMap<>();
        for (Object[] row : saleRepository.dailyRevenueTrend(branchIds, range[0], range[1])) {
            LocalDate day = toLocalDate(row[0]);
            revenueAndCount.put(day, new BigDecimal[]{(BigDecimal) row[1], BigDecimal.valueOf(((Number) row[2]).longValue())});
        }
        Map<LocalDate, BigDecimal> cogsByDay = new LinkedHashMap<>();
        for (Object[] row : saleBatchAllocationRepository.dailyCogsTrend(branchIds, range[0], range[1])) {
            cogsByDay.put(toLocalDate(row[0]), (BigDecimal) row[1]);
        }

        List<DailyFigureResponse> trend = new ArrayList<>();
        for (var entry : revenueAndCount.entrySet()) {
            BigDecimal revenue = entry.getValue()[0];
            long salesCount = entry.getValue()[1].longValue();
            BigDecimal cogs = cogsByDay.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            trend.add(new DailyFigureResponse(entry.getKey(), revenue, cogs, revenue.subtract(cogs), salesCount));
        }
        return trend;
    }

    /** Revenue growth: current period's revenue vs the immediately preceding period of equal length. */
    @Transactional(readOnly = true)
    public GrowthResponse getRevenueGrowth(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                            LocalDate from, LocalDate to) {
        return growth(scope, specificBranchId, from, to, com.company.erp.finance.dto.BranchPnLResponse::revenue);
    }

    /** Profit growth: current period's net profit vs the immediately preceding period of equal length. */
    @Transactional(readOnly = true)
    public GrowthResponse getProfitGrowth(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                           LocalDate from, LocalDate to) {
        return growth(scope, specificBranchId, from, to, com.company.erp.finance.dto.BranchPnLResponse::netProfit);
    }

    /**
     * One P&L row per branch the caller can see - satisfies both "Branch
     * comparison" and "Sales by branch" (spec 27 lists them separately;
     * backend-wise they're the same data). For a branch-scoped (non-admin)
     * user this naturally degenerates to a single-row list, since
     * resolveReadableBranchIds already collapses to their own branch.
     */
    @Transactional(readOnly = true)
    public List<BranchComparisonRow> getBranchComparison(BranchAccessService.BranchScope scope,
                                                           UUID specificBranchId, LocalDate from, LocalDate to) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        List<Branch> branches = branchRepository.findAllById(branchIds);
        List<BranchComparisonRow> rows = new ArrayList<>();
        for (Branch branch : branches) {
            var pnl = financeReportService.getPnL(BranchAccessService.BranchScope.SPECIFIC, branch.getId(), from, to);
            rows.add(BranchComparisonRow.of(branch.getId(), branch.getName(), branch.getType().name(), pnl));
        }
        rows.sort((a, b) -> b.revenue().compareTo(a.revenue()));
        return rows;
    }

    private List<ProductPerformanceResponse> productPerformance(BranchAccessService.BranchScope scope,
                                                                  UUID specificBranchId, LocalDate from,
                                                                  LocalDate to) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        Instant[] range = toInstantRange(from, to);
        return saleItemRepository.sumQuantityAndRevenueByProduct(branchIds, SaleStatus.COMPLETED, range[0], range[1])
                .stream()
                .map(row -> new ProductPerformanceResponse(
                        (UUID) row[0], (String) row[1], (String) row[2],
                        ((Number) row[3]).longValue(), (BigDecimal) row[4]))
                .toList();
    }

    private GrowthResponse growth(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                   LocalDate from, LocalDate to,
                                   java.util.function.Function<com.company.erp.finance.dto.BranchPnLResponse, BigDecimal> metric) {
        if (from.isAfter(to)) {
            throw new BusinessRuleViolationException("'from' date must not be after 'to' date");
        }
        long lengthInDays = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(lengthInDays - 1);

        var current = financeReportService.getPnL(scope, specificBranchId, from, to);
        var previous = financeReportService.getPnL(scope, specificBranchId, previousFrom, previousTo);

        BigDecimal currentValue = metric.apply(current);
        BigDecimal previousValue = metric.apply(previous);
        BigDecimal growthPercent = previousValue.signum() == 0
                ? BigDecimal.ZERO
                : currentValue.subtract(previousValue).multiply(BigDecimal.valueOf(100))
                        .divide(previousValue.abs(), 2, RoundingMode.HALF_UP);

        return new GrowthResponse(from, to, previousFrom, previousTo, currentValue, previousValue, growthPercent);
    }

    private Instant[] toInstantRange(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
        return new Instant[]{fromInstant, toInstant};
    }

    private LocalDate toLocalDate(Object dbValue) {
        Instant instant = dbValue instanceof Timestamp ts ? ts.toInstant() : (Instant) dbValue;
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
