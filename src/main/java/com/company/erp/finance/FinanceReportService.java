package com.company.erp.finance;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.expense.ExpenseRepository;
import com.company.erp.finance.dto.BranchPnLResponse;
import com.company.erp.finance.dto.FinancialTransactionResponse;
import com.company.erp.returns.customer.CustomerReturnItemRepository;
import com.company.erp.sales.SaleBatchAllocationRepository;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.SaleStatus;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Computes the branch P&L (spec section 24) directly from the actual
 * source entities - Sale, SaleBatchAllocation, CustomerReturn, Expense -
 * never by summing FinancialTransaction. See FinancialTransaction's
 * javadoc for the full reasoning: that table is an audit ledger, not an
 * aggregate cache, exactly like StockHistory is for stock.
 * <p>
 * Dates are business calendar dates (LocalDate) per the spec's "date"
 * fields; Sale/CustomerReturn are timestamped (Instant), so a [from, to]
 * LocalDate range is converted to a start-of-day/end-of-day Instant range
 * using the server's default zone. A deployment spanning multiple
 * timezones would need a configured business timezone instead - out of
 * scope for this phase.
 */
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private final SaleRepository saleRepository;
    private final SaleBatchAllocationRepository saleBatchAllocationRepository;
    private final CustomerReturnItemRepository customerReturnItemRepository;
    private final ExpenseRepository expenseRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public BranchPnLResponse getPnL(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                     LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleViolationException("'from' date must not be after 'to' date");
        }
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);

        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);

        BigDecimal grossRevenue = saleRepository.sumRevenueForBranchesInRange(
                branchIds, SaleStatus.COMPLETED, fromInstant, toInstant);
        BigDecimal grossCogs = saleBatchAllocationRepository.sumCogsForBranchesInRange(
                branchIds, SaleStatus.COMPLETED, fromInstant, toInstant);
        BigDecimal refundedRevenue = customerReturnItemRepository.sumRefundedRevenueForBranchesInRange(
                branchIds, fromInstant, toInstant);
        BigDecimal reversedCogs = customerReturnItemRepository.sumReversedCogsForBranchesInRange(
                branchIds, fromInstant, toInstant);
        BigDecimal totalExpenses = expenseRepository.sumForBranchesInRange(branchIds, from, to);

        BigDecimal revenue = grossRevenue.subtract(refundedRevenue);
        BigDecimal cogs = grossCogs.subtract(reversedCogs);
        BigDecimal grossProfit = revenue.subtract(cogs);
        BigDecimal netProfit = grossProfit.subtract(totalExpenses);
        BigDecimal profitMargin = revenue.signum() == 0
                ? BigDecimal.ZERO
                : netProfit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);

        return new BranchPnLResponse(from, to, revenue, cogs, grossProfit, totalExpenses, netProfit, profitMargin);
    }

    /** The audit-trail drill-down listing - see FinancialTransaction's javadoc for why this is separate from getPnL. */
    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> searchTransactions(
            BranchAccessService.BranchScope scope, UUID specificBranchId, FinancialTransactionType type,
            LocalDate from, LocalDate to, Pageable pageable) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = financialTransactionRepository.search(branchIds, type, from, to, pageable)
                .map(FinancialTransactionResponse::from);
        return PageResponse.of(page);
    }
}
