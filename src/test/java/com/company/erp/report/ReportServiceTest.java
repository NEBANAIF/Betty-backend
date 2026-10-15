package com.company.erp.report;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.finance.FinanceReportService;
import com.company.erp.finance.dto.BranchPnLResponse;
import com.company.erp.sales.SaleBatchAllocationRepository;
import com.company.erp.sales.SaleItemRepository;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private SaleItemRepository saleItemRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private SaleBatchAllocationRepository saleBatchAllocationRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private FinanceReportService financeReportService;

    private ReportService service;

    private final UUID branchId = UUID.randomUUID();
    private final UUID productA = UUID.randomUUID();
    private final UUID productB = UUID.randomUUID();
    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        service = new ReportService(saleItemRepository, saleRepository, saleBatchAllocationRepository,
                branchRepository, branchAccessService, financeReportService);

        lenient().when(branchAccessService.resolveReadableBranchIds(any(), any())).thenReturn(List.of(branchId));
    }

    @Test
    void bestSellingProductsAreOrderedByQuantityDescendingAndRespectLimit() {
        // Repository already orders by SUM(quantity) DESC - product A sold more than B.
        when(saleItemRepository.sumQuantityAndRevenueByProduct(anyList(), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{productA, "Cable 2.5mm", "SKU-A", 50L, BigDecimal.valueOf(7500)},
                        new Object[]{productB, "Cable 4mm", "SKU-B", 10L, BigDecimal.valueOf(2000)}));

        var result = service.getBestSellingProducts(BranchAccessService.BranchScope.ALL, null, from, to, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(productA);
        assertThat(result.get(0).quantitySold()).isEqualTo(50L);
    }

    @Test
    void slowMovingProductsAreOrderedByQuantityAscending() {
        when(saleItemRepository.sumQuantityAndRevenueByProduct(anyList(), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{productA, "Cable 2.5mm", "SKU-A", 50L, BigDecimal.valueOf(7500)},
                        new Object[]{productB, "Cable 4mm", "SKU-B", 3L, BigDecimal.valueOf(600)}));

        var result = service.getSlowMovingProducts(BranchAccessService.BranchScope.ALL, null, from, to, 10);

        assertThat(result.get(0).productId()).isEqualTo(productB);
        assertThat(result.get(0).quantitySold()).isEqualTo(3L);
    }

    @Test
    void revenueGrowthComparesAgainstTheImmediatelyPrecedingPeriodOfEqualLength() {
        // 30-day window (Sep 1-30) -> previous period must be Aug 2-31 (30 days).
        var currentPnl = pnl(BigDecimal.valueOf(4000));
        var previousPnl = pnl(BigDecimal.valueOf(2000));
        when(financeReportService.getPnL(any(), any(), eq(from), eq(to))).thenReturn(currentPnl);
        when(financeReportService.getPnL(any(), any(), eq(LocalDate.of(2026, 8, 2)), eq(LocalDate.of(2026, 8, 31))))
                .thenReturn(previousPnl);

        var result = service.getRevenueGrowth(BranchAccessService.BranchScope.ALL, null, from, to);

        assertThat(result.previousFrom()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(result.previousTo()).isEqualTo(LocalDate.of(2026, 8, 31));
        // (4000 - 2000) / 2000 * 100 = 100.00
        assertThat(result.growthPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void growthWithZeroPreviousValueDoesNotDivideByZero() {
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(java.time.temporal.ChronoUnit.DAYS.between(from, to));
        when(financeReportService.getPnL(any(), any(), eq(from), eq(to))).thenReturn(pnl(BigDecimal.valueOf(500)));
        when(financeReportService.getPnL(any(), any(), eq(previousFrom), eq(previousTo))).thenReturn(pnl(BigDecimal.ZERO));

        var result = service.getRevenueGrowth(BranchAccessService.BranchScope.ALL, null, from, to);

        assertThat(result.growthPercent()).isEqualByComparingTo("0");
    }

    @Test
    void growthRejectsAFromDateAfterToDate() {
        assertThatThrownBy(() -> service.getRevenueGrowth(BranchAccessService.BranchScope.ALL, null, to, from))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void branchComparisonIsSortedByRevenueDescending() {
        Branch bole = branch("Bole Store", BranchType.STORE);
        Branch warehouse = branch("Main Warehouse", BranchType.WAREHOUSE);
        when(branchRepository.findAllById(anyList())).thenReturn(List.of(bole, warehouse));
        when(financeReportService.getPnL(eq(BranchAccessService.BranchScope.SPECIFIC), eq(bole.getId()), any(), any()))
                .thenReturn(pnl(BigDecimal.valueOf(1000)));
        when(financeReportService.getPnL(eq(BranchAccessService.BranchScope.SPECIFIC), eq(warehouse.getId()), any(), any()))
                .thenReturn(pnl(BigDecimal.valueOf(5000)));

        var result = service.getBranchComparison(BranchAccessService.BranchScope.ALL, null, from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).branchName()).isEqualTo("Main Warehouse");
        assertThat(result.get(1).branchName()).isEqualTo("Bole Store");
    }

    private BranchPnLResponse pnl(BigDecimal revenue) {
        return new BranchPnLResponse(from, to, revenue, BigDecimal.ZERO, revenue, BigDecimal.ZERO, revenue, BigDecimal.ZERO);
    }

    private Branch branch(String name, BranchType type) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setType(type);
        setId(branch, UUID.randomUUID());
        return branch;
    }

    private void setId(BaseEntity entity, UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
