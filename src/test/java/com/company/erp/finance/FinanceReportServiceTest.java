package com.company.erp.finance;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.expense.ExpenseRepository;
import com.company.erp.returns.customer.CustomerReturnItemRepository;
import com.company.erp.sales.SaleBatchAllocationRepository;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.SaleStatus;
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
class FinanceReportServiceTest {

    @Mock private SaleRepository saleRepository;
    @Mock private SaleBatchAllocationRepository saleBatchAllocationRepository;
    @Mock private CustomerReturnItemRepository customerReturnItemRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;
    @Mock private BranchAccessService branchAccessService;

    private FinanceReportService service;

    private final UUID branchId = UUID.randomUUID();
    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        service = new FinanceReportService(saleRepository, saleBatchAllocationRepository,
                customerReturnItemRepository, expenseRepository, financialTransactionRepository,
                branchAccessService);

        lenient().when(branchAccessService.resolveReadableBranchIds(any(), any()))
                .thenReturn(List.of(branchId));
        // Default everything to zero so each test only has to stub what it cares about.
        lenient().when(saleRepository.sumRevenueForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(saleBatchAllocationRepository.sumCogsForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(customerReturnItemRepository.sumRefundedRevenueForBranchesInRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(customerReturnItemRepository.sumReversedCogsForBranchesInRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(expenseRepository.sumForBranchesInRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void matchesTheSpecsOwnAcceptanceScenarioNumbers() {
        // Main Warehouse -> Bole transfer, sell 20 @ 150 costed at 110:
        // revenue 3000, COGS 2200, gross profit 800 (spec section 48).
        when(saleRepository.sumRevenueForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.valueOf(3000));
        when(saleBatchAllocationRepository.sumCogsForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.valueOf(2200));

        var result = service.getPnL(BranchAccessService.BranchScope.SPECIFIC, branchId, from, to);

        assertThat(result.revenue()).isEqualByComparingTo("3000");
        assertThat(result.cogs()).isEqualByComparingTo("2200");
        assertThat(result.grossProfit()).isEqualByComparingTo("800");
    }

    @Test
    void netProfitAndMarginSubtractExpensesOnTopOfGrossProfit() {
        when(saleRepository.sumRevenueForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.valueOf(3000));
        when(saleBatchAllocationRepository.sumCogsForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.valueOf(2200));
        when(expenseRepository.sumForBranchesInRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.valueOf(200));

        var result = service.getPnL(BranchAccessService.BranchScope.SPECIFIC, branchId, from, to);

        assertThat(result.netProfit()).isEqualByComparingTo("600");
        // 600 / 3000 * 100 = 20.00
        assertThat(result.profitMarginPercent()).isEqualByComparingTo("20.00");
    }

    @Test
    void returnsNetOutOfRevenueAndOnlyTheRestockedPortionNetsOutOfCogs() {
        when(saleRepository.sumRevenueForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.valueOf(3000));
        when(saleBatchAllocationRepository.sumCogsForBranchesInRange(anyList(), eq(SaleStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.valueOf(2200));
        // A customer returned 2 units at 150 each = 300 refunded, restocked, batch cost 110 each = 220 reversed COGS.
        when(customerReturnItemRepository.sumRefundedRevenueForBranchesInRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.valueOf(300));
        when(customerReturnItemRepository.sumReversedCogsForBranchesInRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.valueOf(220));

        var result = service.getPnL(BranchAccessService.BranchScope.SPECIFIC, branchId, from, to);

        assertThat(result.revenue()).isEqualByComparingTo("2700");
        assertThat(result.cogs()).isEqualByComparingTo("1980");
        assertThat(result.grossProfit()).isEqualByComparingTo("720");
    }

    @Test
    void zeroRevenueProducesZeroMarginNotADivideByZero() {
        var result = service.getPnL(BranchAccessService.BranchScope.SPECIFIC, branchId, from, to);

        assertThat(result.revenue()).isEqualByComparingTo("0");
        assertThat(result.profitMarginPercent()).isEqualByComparingTo("0");
    }

    @Test
    void aFromDateAfterToDateIsRejected() {
        assertThatThrownBy(() -> service.getPnL(BranchAccessService.BranchScope.SPECIFIC, branchId, to, from))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
