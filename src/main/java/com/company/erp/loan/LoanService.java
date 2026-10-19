package com.company.erp.loan;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.finance.FinancialReferenceType;
import com.company.erp.finance.FinancialTransactionService;
import com.company.erp.finance.FinancialTransactionType;
import com.company.erp.loan.dto.LoanResponse;
import com.company.erp.loan.dto.LoanSummaryResponse;
import com.company.erp.loan.dto.RecordLoanPaymentRequest;
import com.company.erp.bank.BankResolver;
import com.company.erp.payment.Payment;
import com.company.erp.payment.PaymentReferenceType;
import com.company.erp.payment.PaymentRepository;
import com.company.erp.payment.dto.PaymentResponse;
import com.company.erp.sales.PaymentStatus;
import com.company.erp.sales.Sale;
import com.company.erp.security.BranchAccessService;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Loan lifecycle per spec section 15: created once from an unpaid balance
 * on a Sale, paid down over time via recordPayment, never created or
 * mutated any other way.
 */
@Service
@RequiredArgsConstructor
public class LoanService {

    /** Used when POS checkout doesn't specify a due date explicitly. */
    private static final int DEFAULT_DUE_DAYS = 30;

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final BranchAccessService branchAccessService;
    private final UserRepository userRepository;
    private final FinancialTransactionService financialTransactionService;
    private final BankResolver bankResolver;

    /**
     * Called exclusively from PosCheckoutService, inside the same
     * transaction as the sale itself, immediately after a sale is
     * persisted with remainingAmount > 0. PosCheckoutService has already
     * verified the sale has a customer (credit can't go to a walk-in) -
     * this method re-asserts that invariant defensively rather than
     * trusting the caller blindly.
     */
    @Transactional
    public Loan createFromSale(Sale sale, LocalDate requestedDueDate) {
        if (sale.getCustomer() == null) {
            throw new BusinessRuleViolationException("A loan cannot be created for a sale with no customer");
        }
        if (sale.getRemainingAmount().signum() <= 0) {
            throw new BusinessRuleViolationException("A loan cannot be created for a fully paid sale");
        }
        if (loanRepository.existsBySaleId(sale.getId())) {
            throw new BusinessRuleViolationException("Sale " + sale.getSaleNumber() + " already has a loan");
        }

        Loan loan = new Loan();
        loan.setCustomer(sale.getCustomer());
        loan.setSale(sale);
        loan.setBranch(sale.getBranch());
        loan.setOriginalAmount(sale.getRemainingAmount());
        loan.setPaidAmount(BigDecimal.ZERO);
        loan.setRemainingAmount(sale.getRemainingAmount());
        loan.setStatus(LoanStatus.OPEN);
        loan.setDueDate(requestedDueDate != null ? requestedDueDate : LocalDate.now().plusDays(DEFAULT_DUE_DAYS));

        return loanRepository.save(loan);
    }

    /**
     * Records a payment against an existing loan: creates a Payment row
     * (reference type LOAN_PAYMENT, referencing the loan's own id, never
     * the frontend's say-so on which branch it belongs to) and updates the
     * loan's running balance and status in the same transaction. Posts a
     * PAYMENT_IN ledger entry only - never SALE_REVENUE again, since the
     * full sale total (including this credit portion) was already
     * recognized as revenue at checkout (PosCheckoutService). A loan
     * payment is purely a cash-flow event, not a new sale.
     */
    @Transactional
    public LoanResponse recordPayment(UUID loanId, RecordLoanPaymentRequest request) {
        Loan loan = findOrThrow(loanId);
        // A loan is scoped to the branch its originating sale happened at -
        // only someone with write access to that branch may collect against it.
        branchAccessService.assertCanWriteToBranch(loan.getBranch().getId());

        if (loan.getStatus() == LoanStatus.PAID) {
            throw new BusinessRuleViolationException("Loan for sale " + loan.getSale().getSaleNumber() +
                    " is already fully paid");
        }
        if (request.amount().compareTo(loan.getRemainingAmount()) > 0) {
            throw new BusinessRuleViolationException(
                    "Payment of " + request.amount() + " exceeds the remaining balance of " +
                            loan.getRemainingAmount());
        }

        User receivedBy = userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();

        Payment payment = new Payment();
        payment.setMethod(request.method());
        payment.setAmount(request.amount());
        payment.setBranch(loan.getBranch());
        payment.setReferenceType(PaymentReferenceType.LOAN_PAYMENT);
        payment.setReferenceId(loan.getId());
        bankResolver.attach(payment, request.method(), request.bankId(), request.bankName(), request.accountReference());
        payment.setReceivedBy(receivedBy);
        paymentRepository.save(payment);

        loan.setPaidAmount(loan.getPaidAmount().add(request.amount()));
        loan.setRemainingAmount(loan.getOriginalAmount().subtract(loan.getPaidAmount()));
        loan.setStatus(deriveStatus(loan.getPaidAmount(), loan.getRemainingAmount()));
        syncSaleWithLoan(loan, request.amount());

        financialTransactionService.record(loan.getBranch(), FinancialTransactionType.PAYMENT_IN, request.amount(),
                LocalDate.now(), FinancialReferenceType.LOAN_PAYMENT, loan.getId());

        return LoanResponse.from(loan, paymentHistory(loan.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanSummaryResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                      UUID customerId, LoanStatus status,
                                                      java.time.LocalDate dueFrom, java.time.LocalDate dueTo,
                                                      String search, boolean outstandingOnly,
                                                      Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        // OVERDUE is never a persisted status (see LoanStatus) - translate
        // it into the due-date condition instead of an equality filter
        // that would never match a stored row.
        boolean overdueOnly = status == LoanStatus.OVERDUE;
        LoanStatus persistedFilter = overdueOnly ? null : status;
        var page = loanRepository.search(branchIds, customerId, persistedFilter, overdueOnly, outstandingOnly, dueFrom, dueTo, search, pageable)
                .map(LoanSummaryResponse::from);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public LoanResponse get(UUID id) {
        Loan loan = findOrThrow(id);
        branchAccessService.resolveReadableBranchId(loan.getBranch().getId());
        return LoanResponse.from(loan, paymentHistory(id));
    }

    private List<PaymentResponse> paymentHistory(UUID loanId) {
        return paymentRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(PaymentReferenceType.LOAN_PAYMENT, loanId)
                .stream().map(PaymentResponse::from).toList();
    }

    private LoanStatus deriveStatus(BigDecimal paidAmount, BigDecimal remainingAmount) {
        if (remainingAmount.signum() <= 0) {
            return LoanStatus.PAID;
        }
        return paidAmount.signum() > 0 ? LoanStatus.PARTIALLY_PAID : LoanStatus.OPEN;
    }

    /**
     * Writes down a loan's debt because the underlying goods were
     * returned - this is NOT a payment: no money changes hands here, so
     * unlike recordPayment above, there is no Payment row and no
     * PAYMENT_IN posted. Called exclusively from CustomerReturnService
     * when a returned sale item was sold on credit. Reduces
     * originalAmount and remainingAmount by min(amount, remainingAmount)
     * - a return can never take a loan negative or erase money the
     * customer already paid down.
     *
     * @return the portion of {@code amount} actually applied to the loan.
     *         The caller treats (amount - returned value) as the leftover
     *         cash/bank refund still owed to the customer, since that
     *         portion really was collected as cash and now needs an
     *         actual PAYMENT_OUT - only the credit portion is a debt
     *         write-down rather than money moving.
     */
    @Transactional
    public BigDecimal reduceForReturn(Loan loan, BigDecimal amount) {
        BigDecimal applied = amount.min(loan.getRemainingAmount());
        if (applied.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        loan.setOriginalAmount(loan.getOriginalAmount().subtract(applied));
        loan.setRemainingAmount(loan.getOriginalAmount().subtract(loan.getPaidAmount()));
        loan.setStatus(deriveStatus(loan.getPaidAmount(), loan.getRemainingAmount()));
        syncSaleWithLoan(loan, BigDecimal.ZERO);
        return applied;
    }

    /**
     * Keeps the sale the loan belongs to in step with it, so Sales History tells the same story as the Loans
     * page: money collected on the loan counts as paid on the sale, "owed" is whatever the loan still owes, and
     * the sale becomes PAID once nothing is owed (it stays PARTIAL while something is, or CREDIT if nothing at
     * all has been paid yet). {@code collected} is cash/bank just received (zero for a return write-down).
     */
    private void syncSaleWithLoan(Loan loan, BigDecimal collected) {
        Sale sale = loan.getSale();
        if (sale == null) {
            return;
        }
        BigDecimal paidSoFar = sale.getPaidAmount() == null ? BigDecimal.ZERO : sale.getPaidAmount();
        sale.setPaidAmount(paidSoFar.add(collected));
        sale.setRemainingAmount(loan.getRemainingAmount());
        if (loan.getRemainingAmount().signum() <= 0) {
            sale.setPaymentStatus(PaymentStatus.PAID);
        } else if (sale.getPaidAmount().signum() > 0) {
            sale.setPaymentStatus(PaymentStatus.PARTIAL);
        }
    }

    /**
     * Removes a loan because the sale that created it was voided (see
     * SaleVoidService) - not a return, not a payment, the loan simply
     * should never have existed. Refuses if any payment has already been
     * recorded against it: real cash/bank was collected in that case, and
     * that needs a deliberate, visible reversal (recordPayment's mirror,
     * or a manual write-off) rather than disappearing silently inside a
     * void - SaleVoidService checks this same condition before it ever
     * gets here, so this is a defensive second check, not the only one.
     * <p>
     * Unlike everything else a void touches (the Sale row, StockHistory,
     * FinancialTransaction), the loan itself is deleted rather than kept
     * and marked cancelled: LoanStatus has no CANCELLED value because a
     * loan is a running balance, not a ledger entry, and this one is only
     * ever reachable with zero payments and thus zero real activity to
     * preserve. The money-relevant audit trail lives on the Sale (voided,
     * with a reason) and the FinancialTransaction reversal rows in
     * SaleVoidService, not here.
     */
    @Transactional
    public void cancelForVoid(Loan loan) {
        if (loan.getPaidAmount().signum() > 0) {
            throw new BusinessRuleViolationException(
                    "Loan for sale " + loan.getSale().getSaleNumber() +
                            " already has a payment recorded - reverse that payment before voiding the sale");
        }
        loanRepository.delete(loan);
    }

    private Loan findOrThrow(UUID id) {
        return loanRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Loan", id));
    }
}
