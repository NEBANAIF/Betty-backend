-- Optional expiry date for stock batches (e.g. cosmetics with a shelf life).
-- Nullable: most products (electronics, etc.) never set this.
ALTER TABLE product_batches ADD COLUMN expiry_date DATE;
ALTER TABLE stock_change_requests ADD COLUMN expiry_date DATE;
