-- The broad "view" switches used to open several pages at once. They are split so each page
-- has its own switch (see Permission.java). To change nothing for anyone today, every role
-- (built-in or custom) that held the broad switch is given the new page switches it used to
-- open, so access stays exactly as it was until a Super Admin edits the role.

-- INVENTORY_VIEW opened Inventory, Batches and Stock History.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, p.permission
FROM access_role_permissions
CROSS JOIN (VALUES ('BATCH_VIEW'), ('STOCK_HISTORY_VIEW')) AS p (permission)
WHERE access_role_permissions.permission = 'INVENTORY_VIEW'
ON CONFLICT DO NOTHING;

-- SALES_VIEW opened Sales History, Customers, Loans and the customer-returns list.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, p.permission
FROM access_role_permissions
CROSS JOIN (VALUES ('CUSTOMER_VIEW'), ('LOAN_VIEW'), ('RETURN_VIEW')) AS p (permission)
WHERE access_role_permissions.permission = 'SALES_VIEW'
ON CONFLICT DO NOTHING;

-- FINANCE_VIEW also opened the Loans and Expenses lists.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, p.permission
FROM access_role_permissions
CROSS JOIN (VALUES ('LOAN_VIEW'), ('EXPENSE_VIEW')) AS p (permission)
WHERE access_role_permissions.permission = 'FINANCE_VIEW'
ON CONFLICT DO NOTHING;
