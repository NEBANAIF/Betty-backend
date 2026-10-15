-- Product categories are removed entirely: products no longer belong to a
-- category and the categories table is dropped. (Expense categories are a
-- different thing - a fixed list on each expense - and are not touched.)
-- Dropping products.category_id also drops its foreign key and index.
ALTER TABLE products DROP COLUMN category_id;
DROP TABLE categories;

-- The "manage categories" switch no longer exists in the Permission enum;
-- clear it out of every role that held it.
DELETE FROM access_role_permissions WHERE permission = 'CATEGORY_MANAGE';

-- Adding a batch directly (Batches > Add Batch), without a purchase order.
-- It gets its own movement type so Stock History can tell it apart from a
-- purchase receipt or a manual adjustment.
ALTER TABLE stock_history DROP CONSTRAINT stock_history_movement_type_check;
ALTER TABLE stock_history ADD CONSTRAINT stock_history_movement_type_check CHECK (movement_type IN
    ('PURCHASE', 'SALE', 'TRANSFER_OUT', 'TRANSFER_IN',
     'CUSTOMER_RETURN', 'SUPPLIER_RETURN', 'ADJUSTMENT', 'DAMAGED', 'LOST', 'SALE_VOID',
     'BATCH_ADDED'));

-- New permission BATCH_CREATE. Every role that could already adjust stock
-- (which could already add stock by hand) keeps that ability under the new
-- switch; grant it to other roles on the Roles & Permissions page.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, 'BATCH_CREATE'
FROM access_role_permissions
WHERE permission = 'STOCK_ADJUST'
ON CONFLICT DO NOTHING;
