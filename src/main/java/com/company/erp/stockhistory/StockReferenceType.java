package com.company.erp.stockhistory;

/**
 * What kind of business document caused a stock movement. Paired with a
 * referenceId (UUID) on StockHistory to let the UI deep-link "View Sale",
 * "View Transfer", etc. from a ledger row.
 */
public enum StockReferenceType {
    PURCHASE_ORDER,
    SALE,
    TRANSFER,
    CUSTOMER_RETURN,
    SUPPLIER_RETURN,
    MANUAL_ADJUSTMENT,
    SALE_VOID
}
