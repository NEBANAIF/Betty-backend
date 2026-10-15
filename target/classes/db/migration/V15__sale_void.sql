-- Sale Void (Sales History): lets a genuinely wrong/duplicate sale be
-- reversed instead of deleted - see SaleVoidService's javadoc for the
-- full reasoning. This is deliberately its own movement/reference type
-- rather than reusing CUSTOMER_RETURN: a void means the sale itself was
-- never valid, whereas a return means the sale was valid and goods
-- physically came back - the ledger should be able to tell those apart.

ALTER TABLE stock_history DROP CONSTRAINT stock_history_movement_type_check;
ALTER TABLE stock_history ADD CONSTRAINT stock_history_movement_type_check CHECK (movement_type IN
    ('PURCHASE', 'SALE', 'TRANSFER_OUT', 'TRANSFER_IN',
     'CUSTOMER_RETURN', 'SUPPLIER_RETURN', 'ADJUSTMENT', 'DAMAGED', 'LOST', 'SALE_VOID'));

ALTER TABLE stock_history DROP CONSTRAINT stock_history_reference_type_check;
ALTER TABLE stock_history ADD CONSTRAINT stock_history_reference_type_check CHECK (reference_type IN
    ('PURCHASE_ORDER', 'SALE', 'TRANSFER', 'CUSTOMER_RETURN',
     'SUPPLIER_RETURN', 'MANUAL_ADJUSTMENT', 'SALE_VOID'));

ALTER TABLE financial_transactions DROP CONSTRAINT financial_transactions_reference_type_check;
ALTER TABLE financial_transactions ADD CONSTRAINT financial_transactions_reference_type_check CHECK (reference_type IN
    ('SALE', 'EXPENSE', 'LOAN_PAYMENT', 'CUSTOMER_RETURN', 'SALE_VOID'));

-- A voided sale's loan (only reachable when it has zero payments recorded
-- against it - see SaleVoidService) is deleted outright rather than kept
-- around in a dead state, since LoanStatus has no CANCELLED value and the
-- loan never had any real activity to preserve.
ALTER TABLE sales ADD COLUMN void_reason VARCHAR(500);
