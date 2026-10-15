package com.company.erp.payment;

/**
 * What business transaction a Payment is settling. LOAN_PAYMENT and
 * SUPPLIER_PAYMENT are wired up when Loans (Phase 7) and Supplier
 * settlement exist; SALE is live from this phase.
 */
public enum PaymentReferenceType {
    SALE,
    LOAN_PAYMENT,
    SUPPLIER_PAYMENT
}
