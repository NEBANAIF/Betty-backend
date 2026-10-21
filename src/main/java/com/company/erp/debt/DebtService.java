package com.company.erp.debt;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.debt.dto.DebtPaymentRequest;
import com.company.erp.debt.dto.DebtPaymentResponse;
import com.company.erp.debt.dto.DebtRequest;
import com.company.erp.debt.dto.DebtResponse;
import com.company.erp.debt.dto.DebtSummaryResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.erp.purchase.PurchaseOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The business's own debts (what we owe). Self-contained on purpose: recording a debt or a repayment here
 * does not touch sales, stock, customer loans or the profit figures - a repayment of principal is not an
 * expense. (Interest you want counted as a cost can be logged as a normal expense.)
 */
@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository paymentRepository;
    private final BranchAccessService branchAccessService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<DebtResponse> search(DebtType type, DebtStatus status, boolean outstandingOnly,
                                             boolean overdueOnly, String search, Pageable pageable) {
        return PageResponse.of(debtRepository.search(type, status, outstandingOnly, overdueOnly, search, pageable)
                .map(d -> DebtResponse.from(d, List.of())));
    }

    @Transactional(readOnly = true)
    public DebtResponse get(UUID id) {
        return toDetail(find(id));
    }

    @Transactional(readOnly = true)
    public DebtSummaryResponse summary() {
        LocalDate today = LocalDate.now();
        BigDecimal borrowed = BigDecimal.ZERO, paid = BigDecimal.ZERO, remaining = BigDecimal.ZERO, overdueRemaining = BigDecimal.ZERO;
        long open = 0, overdue = 0;
        for (Debt d : debtRepository.findAll()) {
            if (d.getStatus() == DebtStatus.CANCELLED) {
                continue;
            }
            borrowed = borrowed.add(d.getTotalAmount());
            paid = paid.add(d.getPaidAmount());
            remaining = remaining.add(d.getRemainingAmount());
            if (d.getStatus() != DebtStatus.PAID) {
                open++;
                if (d.getDueDate() != null && d.getDueDate().isBefore(today)) {
                    overdue++;
                    overdueRemaining = overdueRemaining.add(d.getRemainingAmount());
                }
            }
        }
        return new DebtSummaryResponse(borrowed, paid, remaining, overdueRemaining, open, overdue);
    }

    /**
     * Called by PurchaseOrderService, in the same transaction, when a purchase order is bought on credit: records
     * what we owe the supplier. The amount is what is still unpaid, in the local currency (for a global order,
     * (items total - paid now) x exchange rate); shipping cost is not included. Repayments are then recorded on the credit itself.
     */
    @Transactional
    public Debt createForPurchase(PurchaseOrder po, LocalDate dueDate, User creator) {
        // What is still owed: the order total minus anything paid at order time (PARTIAL), in the local currency.
        BigDecimal amount = po.getTotal().subtract(po.getAmountPaid()).multiply(po.getExchangeRate())
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new BusinessRuleViolationException("A purchase with nothing left to pay cannot be bought on credit");
        }
        Debt debt = new Debt();
        debt.setLenderName(po.getSupplier().getName());
        debt.setDebtType(DebtType.SUPPLIER_CREDIT);
        debt.setDescription(po.getAmountPaid().signum() > 0
                ? "Purchase " + po.getOrderNumber() + " - balance after paying " + po.getAmountPaid().toPlainString() + " now"
                : "Purchase " + po.getOrderNumber());
        debt.setPrincipalAmount(amount);
        debt.setInterestAmount(BigDecimal.ZERO);
        debt.setTotalAmount(amount);
        debt.setPaidAmount(BigDecimal.ZERO);
        debt.setRemainingAmount(amount);
        debt.setStartDate(po.getOrderDate());
        debt.setDueDate(dueDate);
        debt.setStatus(DebtStatus.OPEN);
        debt.setPurchaseOrder(po);
        debt.setCreatedBy(creator);
        return debtRepository.save(debt);
    }

    /**
     * A credit purchase order is being cancelled. If nothing has been repaid yet the credit is marked CANCELLED (it
     * stays on record but leaves every total); if repayments exist the cancel is refused, because that money has
     * already gone to the supplier.
     */
    @Transactional
    public void cancelForPurchase(PurchaseOrder po) {
        debtRepository.findByPurchaseOrderId(po.getId()).ifPresent(debt -> {
            if (debt.getPaidAmount().signum() > 0) {
                throw new BusinessRuleViolationException("Cannot cancel " + po.getOrderNumber() + ": "
                        + debt.getPaidAmount().toPlainString() + " has already been repaid on its credit "
                        + "(My Credits & Loans)");
            }
            debt.setStatus(DebtStatus.CANCELLED);
            debt.setRemainingAmount(BigDecimal.ZERO);
            debtRepository.save(debt);
        });
    }

    @Transactional
    public DebtResponse create(DebtRequest request) {
        validateDates(request);
        Debt debt = new Debt();
        debt.setCreatedBy(currentUserEntity());
        apply(debt, request);
        debt.setPaidAmount(BigDecimal.ZERO);
        debt.setRemainingAmount(debt.getTotalAmount());
        debt.setStatus(DebtStatus.OPEN);
        return toDetail(debtRepository.save(debt));
    }

    /** Edit the details. The total can be changed but never below what has already been repaid. */
    @Transactional
    public DebtResponse update(UUID id, DebtRequest request) {
        validateDates(request);
        Debt debt = find(id);
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            throw new BusinessRuleViolationException("This credit was cancelled and can no longer be edited");
        }
        apply(debt, request);
        if (debt.getTotalAmount().compareTo(debt.getPaidAmount()) < 0) {
            throw new BusinessRuleViolationException("The total to repay cannot be less than the "
                    + debt.getPaidAmount().toPlainString() + " already paid");
        }
        refreshBalance(debt);
        return toDetail(debtRepository.save(debt));
    }

    @Transactional
    public DebtResponse recordPayment(UUID id, DebtPaymentRequest request) {
        Debt debt = find(id);
        if (debt.getStatus() == DebtStatus.PAID) {
            throw new BusinessRuleViolationException("This debt is already fully paid");
        }
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            throw new BusinessRuleViolationException("This credit was cancelled - its purchase order was cancelled");
        }
        if (request.amount().compareTo(debt.getRemainingAmount()) > 0) {
            throw new BusinessRuleViolationException("Payment of " + request.amount().toPlainString()
                    + " is more than the " + debt.getRemainingAmount().toPlainString() + " still owed");
        }
        LocalDate date = request.paymentDate() != null ? request.paymentDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessRuleViolationException("The payment date cannot be in the future");
        }

        DebtPayment payment = new DebtPayment();
        payment.setDebt(debt);
        payment.setAmount(request.amount());
        payment.setPaymentDate(date);
        payment.setMethod(request.method());
        payment.setReference(blankToNull(request.reference()));
        payment.setNote(blankToNull(request.note()));
        payment.setCreatedBy(currentUserEntity());
        paymentRepository.save(payment);

        debt.setPaidAmount(debt.getPaidAmount().add(request.amount()));
        refreshBalance(debt);
        return toDetail(debtRepository.save(debt));
    }

    // ------------------------------------------------------------------ helpers

    private void apply(Debt debt, DebtRequest r) {
        BigDecimal interest = r.interestAmount() != null ? r.interestAmount() : BigDecimal.ZERO;
        debt.setLenderName(r.lenderName().trim());
        debt.setDebtType(r.debtType());
        debt.setDescription(blankToNull(r.description()));
        debt.setPrincipalAmount(r.principalAmount());
        debt.setInterestAmount(interest);
        debt.setTotalAmount(r.principalAmount().add(interest));
        debt.setStartDate(r.startDate());
        debt.setDueDate(r.dueDate());
        debt.setNotes(blankToNull(r.notes()));
    }

    private void refreshBalance(Debt debt) {
        debt.setRemainingAmount(debt.getTotalAmount().subtract(debt.getPaidAmount()));
        if (debt.getRemainingAmount().signum() == 0) {
            debt.setStatus(DebtStatus.PAID);
        } else if (debt.getPaidAmount().signum() > 0) {
            debt.setStatus(DebtStatus.PARTIALLY_PAID);
        } else {
            debt.setStatus(DebtStatus.OPEN);
        }
    }

    private void validateDates(DebtRequest r) {
        if (r.dueDate() != null && r.dueDate().isBefore(r.startDate())) {
            throw new BusinessRuleViolationException("The due date cannot be before the start date");
        }
    }

    private Debt find(UUID id) {
        return debtRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Debt", id));
    }

    private DebtResponse toDetail(Debt debt) {
        List<DebtPaymentResponse> payments = paymentRepository
                .findByDebtIdOrderByPaymentDateDescCreatedAtDesc(debt.getId())
                .stream().map(DebtPaymentResponse::from).toList();
        return DebtResponse.from(debt, payments);
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
