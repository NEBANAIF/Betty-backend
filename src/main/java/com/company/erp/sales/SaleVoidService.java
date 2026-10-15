package com.company.erp.sales;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.finance.FinancialReferenceType;
import com.company.erp.finance.FinancialTransactionService;
import com.company.erp.finance.FinancialTransactionType;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.loan.Loan;
import com.company.erp.loan.LoanRepository;
import com.company.erp.loan.LoanService;
import com.company.erp.notification.NotificationService;
import com.company.erp.notification.NotificationType;
import com.company.erp.sales.dto.SaleResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Voids a sale that should never have existed - a duplicate ring-up, a
 * cashier mistake, bad data entry. Deliberately separate from
 * CustomerReturnService: a return means real goods physically came back
 * against a sale that WAS valid; a void means the sale record itself was
 * wrong from the start, so unlike a return every item is unconditionally
 * restocked and the entire revenue/COGS impact is reversed, not just a
 * chosen subset.
 * <p>
 * Nothing is ever deleted - see this class's callers. The Sale row, its
 * items, its original StockHistory and FinancialTransaction rows all stay
 * exactly as they were; voiding only ever ADDS reversing rows (negative
 * SALE_REVENUE/COGS, a SALE_VOID stock movement putting the stock back)
 * and flips Sale.status to CANCELLED, which is the only thing every
 * report/P&L/dashboard query actually filters on (see
 * FinanceReportService, ReportService, DashboardService - all scoped to
 * SaleStatus.COMPLETED) - so a voided sale disappears from every number
 * automatically without any report needing special-case logic, and the
 * full history of what happened remains inspectable by anyone who opens
 * the sale in Sales History.
 */
@Service
@RequiredArgsConstructor
public class SaleVoidService {

    private final SaleRepository saleRepository;
    private final LoanRepository loanRepository;
    private final LoanService loanService;
    private final StockMutationService stockMutationService;
    private final FinancialTransactionService financialTransactionService;
    private final BranchAccessService branchAccessService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public SaleResponse voidSale(UUID saleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException("A reason is required to void a sale");
        }

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Sale", saleId));
        branchAccessService.assertCanWriteToBranch(sale.getBranch().getId());

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Sale " + sale.getSaleNumber() + " is already voided");
        }

        // A loan with money already collected against it needs a deliberate,
        // visible reversal first - see LoanService.cancelForVoid's javadoc.
        Optional<Loan> loanOpt = loanRepository.findBySaleId(sale.getId());
        if (loanOpt.isPresent() && loanOpt.get().getPaidAmount().signum() > 0) {
            throw new BusinessRuleViolationException(
                    "This sale has a payment recorded against its loan - reverse or refund that payment first, " +
                            "then void the sale");
        }

        User actor = userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
        LocalDate today = LocalDate.now();

        // 1. Restock every allocation back into its exact original batch,
        //    preserving cost - mirrors CustomerReturnService.restockToBatch,
        //    but unconditional (a void restocks everything, never partial).
        BigDecimal totalCogs = BigDecimal.ZERO;
        for (SaleItem item : sale.getItems()) {
            for (SaleBatchAllocation allocation : item.getAllocations()) {
                stockMutationService.restockToBatch(
                        item.getProduct(), sale.getBranch(), allocation.getBatch().getId(),
                        allocation.getQuantityAllocated(), StockMovementType.SALE_VOID,
                        "Void of sale " + sale.getSaleNumber() + ": " + reason,
                        actor, StockReferenceType.SALE_VOID, sale.getId());
                totalCogs = totalCogs.add(
                        allocation.getUnitCost().multiply(BigDecimal.valueOf(allocation.getQuantityAllocated())));
            }
        }

        // 2. Cancel any loan this sale created (guaranteed unpaid by the guard above).
        loanOpt.ifPresent(loanService::cancelForVoid);

        // 3. Reverse the ledger. Revenue/COGS reverse in full (unlike a
        //    return, nothing here is partial). Only the portion actually
        //    collected as cash/bank at checkout (paidAmount) goes back out
        //    as a real PAYMENT_OUT - the credit portion was never cash to
        //    begin with, it was a loan, which step 2 already cancelled.
        financialTransactionService.record(sale.getBranch(), FinancialTransactionType.SALE_REVENUE,
                sale.getTotalAmount().negate(), today, FinancialReferenceType.SALE_VOID, sale.getId());
        if (totalCogs.signum() > 0) {
            financialTransactionService.record(sale.getBranch(), FinancialTransactionType.COGS,
                    totalCogs.negate(), today, FinancialReferenceType.SALE_VOID, sale.getId());
        }
        if (sale.getPaidAmount().signum() > 0) {
            financialTransactionService.record(sale.getBranch(), FinancialTransactionType.PAYMENT_OUT,
                    sale.getPaidAmount(), today, FinancialReferenceType.SALE_VOID, sale.getId());
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setVoidReason(reason);

        notificationService.record(NotificationType.SALE_VOIDED, "Sale voided",
                sale.getSaleNumber() + " · total "
                        + sale.getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " at " + sale.getBranch().getName() + " — " + reason.trim(),
                sale.getBranch(), actor, "SALE", sale.getId());

        return SaleResponse.from(sale);
    }
}
