package com.company.erp.purchase;

/**
 * How the supplier is paid for a purchase order. PAID: the supplier was paid in full up front, nothing is owed.
 * PARTIAL: part was paid now (amountPaid), the balance is owed and recorded as a debt.
 * CREDIT: nothing paid yet - the whole order is recorded as a debt on the My Credits & Loans page.
 */
public enum PurchasePaymentType {
    PAID,
    PARTIAL,
    CREDIT
}
