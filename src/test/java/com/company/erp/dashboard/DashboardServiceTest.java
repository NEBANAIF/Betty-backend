package com.company.erp.dashboard;

import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.expense.ExpenseRepository;
import com.company.erp.finance.FinanceReportService;
import com.company.erp.finance.dto.BranchPnLResponse;
import com.company.erp.inventory.InventoryRepository;
import com.company.erp.report.ReportService;
import com.company.erp.sales.Sale;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.transfer.StockTransfer;
import com.company.erp.transfer.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private FinanceReportService financeReportService;
    @Mock private ReportService reportService;
    @Mock private BranchAccessService branchAccessService;
    @Mock private SaleRepository saleRepository;
    @Mock private StockTransferRepository stockTransferRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductBatchRepository productBatchRepository;

    private DashboardService service;

    private final UUID branchId = UUID.randomUUID();
    private final LocalDate from = LocalDate.of(2026, 9, 16);
    private final LocalDate to = LocalDate.of(2026, 9, 16);

    @BeforeEach
    void setUp() {
        service = new DashboardService(financeReportService, reportService, branchAccessService, saleRepository,
                stockTransferRepository, expenseRepository, inventoryRepository, productBatchRepository);

        lenient().when(branchAccessService.resolveReadableBranchIds(any(), any())).thenReturn(List.of(branchId));
        lenient().when(financeReportService.getPnL(any(), any(), any(), any())).thenReturn(
                new BranchPnLResponse(from, to, BigDecimal.valueOf(3000), BigDecimal.valueOf(2200),
                        BigDecimal.valueOf(800), BigDecimal.valueOf(100), BigDecimal.valueOf(700), BigDecimal.valueOf(23.33)));
        lenient().when(productBatchRepository.sumInventoryValueForBranches(any())).thenReturn(BigDecimal.valueOf(67000));
        lenient().when(inventoryRepository.countLowStock(any())).thenReturn(3L);
        lenient().when(inventoryRepository.countOutOfStock(any())).thenReturn(1L);
        lenient().when(inventoryRepository.countInStock(any())).thenReturn(40L);
        lenient().when(inventoryRepository.findLowStockAlerts(any(), any())).thenReturn(List.of());
        Page<Sale> emptySales = new PageImpl<>(List.of());
        lenient().when(saleRepository.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptySales);
        Page<StockTransfer> emptyTransfers = new PageImpl<>(List.of());
        lenient().when(stockTransferRepository.search(any(), any(), any(), any())).thenReturn(emptyTransfers);
        lenient().when(expenseRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(reportService.getBranchComparison(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void summaryCombinesPnlWithInventoryAndStockLevelKpis() {
        var result = service.getSummary(BranchAccessService.BranchScope.ALL, null, from, to);

        assertThat(result.revenue()).isEqualByComparingTo("3000");
        assertThat(result.cogs()).isEqualByComparingTo("2200");
        assertThat(result.grossProfit()).isEqualByComparingTo("800");
        assertThat(result.inventoryValue()).isEqualByComparingTo("67000");
        assertThat(result.lowStockCount()).isEqualTo(3L);
        assertThat(result.outOfStockCount()).isEqualTo(1L);
        assertThat(result.inStockCount()).isEqualTo(40L);
    }

    @Test
    void lowStockAndOutOfStockAreDistinctCounts() {
        var result = service.getSummary(BranchAccessService.BranchScope.ALL, null, from, to);

        // These must come from two DIFFERENT repository methods, not one
        // count split client-side - confirms no double-counting/overlap.
        assertThat(result.lowStockCount()).isNotEqualTo(result.outOfStockCount());
    }
}
