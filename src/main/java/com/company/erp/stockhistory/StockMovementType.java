package com.company.erp.stockhistory;

public enum StockMovementType {
    PURCHASE,
    SALE,
    TRANSFER_OUT,
    TRANSFER_IN,
    CUSTOMER_RETURN,
    SUPPLIER_RETURN,
    ADJUSTMENT,
    DAMAGED,
    LOST,
    SALE_VOID,
    /** Stock added directly as a new batch (Batches > Add Batch), not via a purchase order. */
    BATCH_ADDED
}
