package com.company.erp.debt;

/**
 * Only OPEN / PARTIALLY_PAID / PAID are stored. "Overdue" is never stored: it is computed when read
 * (not PAID and due date before today), exactly like customer loans.
 */
public enum DebtStatus {
    OPEN,
    PARTIALLY_PAID,
    PAID,
    /** The purchase order this credit came from was cancelled before anything was repaid. Ignored in all totals. */
    CANCELLED
}
