package com.company.erp.stockchange;

/**
 * ADD_BATCH: stock coming in as a new batch (needs a cost). REMOVE_STOCK: stock going out by hand
 * (correction, damaged, lost). EDIT_BATCH: fix an existing batch's number, cost price or expiry
 * date - the quantities are never touched.
 */
public enum StockChangeType {
    ADD_BATCH,
    REMOVE_STOCK,
    EDIT_BATCH
}
