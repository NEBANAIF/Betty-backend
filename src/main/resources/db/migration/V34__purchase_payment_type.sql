-- A purchase order is either paid in full or bought on credit. Credit orders are recorded as a debt
-- (supplier credit) on the My Credits & Loans page, linked back to the order.
ALTER TABLE purchase_orders ADD COLUMN payment_type VARCHAR(10) NOT NULL DEFAULT 'PAID'
    CHECK (payment_type IN ('PAID', 'CREDIT'));

ALTER TABLE debts ADD COLUMN purchase_order_id UUID REFERENCES purchase_orders (id);
CREATE UNIQUE INDEX uk_debts_purchase_order ON debts (purchase_order_id) WHERE purchase_order_id IS NOT NULL;

-- A credit whose purchase order is cancelled (before any repayment) is kept on record as CANCELLED.
ALTER TABLE debts DROP CONSTRAINT IF EXISTS debts_status_check;
ALTER TABLE debts ADD CONSTRAINT debts_status_check
    CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'));
