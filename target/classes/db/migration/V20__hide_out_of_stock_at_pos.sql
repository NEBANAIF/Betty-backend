-- hide_out_of_stock_at_pos: when true, a product with zero available stock at the branch being
-- sold from is left out of the POS product search - a cashier can't ring up something that isn't
-- there. Deliberately scoped to POS only: Purchases, Batches and Transfers must still be able to
-- find an out-of-stock product, since finding it is the whole point of restocking it.
-- Starts FALSE so existing POS behavior is unchanged until a SETTINGS_MANAGE user turns it on.

ALTER TABLE system_settings ADD COLUMN hide_out_of_stock_at_pos BOOLEAN NOT NULL DEFAULT FALSE;
