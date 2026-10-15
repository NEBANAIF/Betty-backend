-- A product no longer carries a selling price. The price is entered on each sale
-- line at the POS (sale_items.unit_price already stores the price actually charged,
-- so no historical sale is affected). Cost still lives only on batches.
ALTER TABLE products DROP COLUMN selling_price;
