-- Third way to pay a purchase: PARTIAL (part paid at order time, the balance goes to My Credits & Loans).
ALTER TABLE purchase_orders DROP CONSTRAINT IF EXISTS purchase_orders_payment_type_check;
ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_payment_type_check
    CHECK (payment_type IN ('PAID', 'PARTIAL', 'CREDIT'));
ALTER TABLE purchase_orders ADD COLUMN amount_paid NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0);
