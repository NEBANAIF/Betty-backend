package com.company.erp.report;

import com.company.erp.inventory.ProductStockService;
import com.company.erp.inventory.dto.StockStatusReportResponse;
import com.company.erp.report.dto.BranchComparisonRow;
import com.company.erp.report.dto.DailyFigureResponse;
import com.company.erp.report.dto.GrowthResponse;
import com.company.erp.report.dto.ProductPerformanceResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Only the analytics that don't already exist elsewhere - see
 * ReportService's javadoc for which spec-27 report pages reuse an
 * existing domain controller's search endpoint instead.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ProductStockService productStockService;

    @GetMapping("/best-selling-products")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<ProductPerformanceResponse> bestSellingProducts(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.getBestSellingProducts(scope, branchId, from, to, limit);
    }

    @GetMapping("/slow-moving-products")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<ProductPerformanceResponse> slowMovingProducts(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.getSlowMovingProducts(scope, branchId, from, to, limit);
    }

    @GetMapping("/sales-by-product")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<ProductPerformanceResponse> salesByProduct(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getSalesByProduct(scope, branchId, from, to);
    }

    @GetMapping("/sales-trend")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<DailyFigureResponse> salesTrend(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getSalesTrend(scope, branchId, from, to);
    }

    @GetMapping("/revenue-growth")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public GrowthResponse revenueGrowth(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getRevenueGrowth(scope, branchId, from, to);
    }

    @GetMapping("/profit-growth")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public GrowthResponse profitGrowth(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getProfitGrowth(scope, branchId, from, to);
    }

    @GetMapping("/branch-comparison")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<BranchComparisonRow> branchComparison(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getBranchComparison(scope, branchId, from, to);
    }

    /**
     * Current stock position (not tied to a date range): how many products are in stock, low and
     * out of stock for the selected branches, plus the products that need restocking.
     */
    @GetMapping("/stock-status")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public StockStatusReportResponse stockStatus(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId) {
        return productStockService.report(scope, branchId);
    }
}
