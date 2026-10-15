package com.company.erp.finance;

/**
 * What kind of business document caused a financial transaction - mirrors
 * StockReferenceType's role for StockHistory. Paired with a referenceId
 * (UUID) on FinancialTransaction to deep-link back to the source document.
 */
public enum FinancialReferenceType {
    SALE,
    EXPENSE,
    LOAN_PAYMENT,
    CUSTOMER_RETURN,
    SALE_VOID
}
