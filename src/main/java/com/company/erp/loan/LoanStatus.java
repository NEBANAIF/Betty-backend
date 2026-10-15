package com.company.erp.loan;

/**
 * OPEN / PARTIALLY_PAID / PAID are the only values ever persisted on
 * {@link Loan#getStatus()} - they transition strictly in that order, driven
 * only by {@code LoanService.recordPayment}.
 * <p>
 * OVERDUE is real (spec section 6) but intentionally never stored: whether
 * a loan is overdue is a pure function of {@code status != PAID} and
 * {@code dueDate < today}, so persisting it would either go stale the
 * instant midnight passes or require a scheduled job to keep correct.
 * {@code LoanService.effectiveStatus} / {@code LoanResponse} compute it at
 * read time instead, and the search endpoint accepts {@code OVERDUE} as a
 * filter value by translating it into that same due-date condition rather
 * than an equality check against the (never-populated) column.
 */
public enum LoanStatus {
    OPEN,
    PARTIALLY_PAID,
    PAID,
    OVERDUE
}
