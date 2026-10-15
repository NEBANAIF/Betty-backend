package com.company.erp.stockchange;

/** PENDING: entered, waiting - stock has NOT moved yet. APPROVED: checked, and the stock has moved. REJECTED: discarded, stock never moved. */
public enum StockChangeStatus {
    PENDING,
    APPROVED,
    REJECTED
}
