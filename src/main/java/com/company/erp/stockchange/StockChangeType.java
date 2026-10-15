package com.company.erp.stockchange;

/** ADD_BATCH: stock coming in as a new batch (needs a cost). REMOVE_STOCK: stock going out by hand (correction, damaged, lost). */
public enum StockChangeType {
    ADD_BATCH,
    REMOVE_STOCK
}
