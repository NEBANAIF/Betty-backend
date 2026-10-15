-- Roles become data. Until now what each role could do was a fixed table in the
-- code; now a role is a named set of permissions stored here, so the Super Admin
-- can edit the five built-in roles and create new ones from the Roles &
-- Permissions page.
--
-- base_role is the STRUCTURAL kind of a role (SUPER_ADMIN has no branch and sees
-- everything; the other four are tied to a store or a warehouse, and a manager
-- kind can be set as a branch manager). It never changes what a role may do -
-- that is entirely access_role_permissions. app_users.role is kept and always
-- equals the user's access role's base_role.

CREATE TABLE access_roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    base_role   VARCHAR(30)  NOT NULL CHECK (base_role IN
                 ('SUPER_ADMIN', 'STORE_MANAGER', 'STORE_STAFF', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_access_roles_name ON access_roles (LOWER(name));

CREATE TABLE access_role_permissions (
    access_role_id UUID        NOT NULL REFERENCES access_roles (id) ON DELETE CASCADE,
    permission     VARCHAR(60) NOT NULL,
    PRIMARY KEY (access_role_id, permission)
);

-- The five built-in roles.
INSERT INTO access_roles (base_role, name, description, system_role) VALUES
    ('SUPER_ADMIN', 'Super Admin', 'Full access to everything. This role cannot be edited.', TRUE),
    ('STORE_MANAGER', 'Store Manager', 'Runs a store.', TRUE),
    ('STORE_STAFF', 'Store Staff', 'Works at a store (cashier).', TRUE),
    ('WAREHOUSE_MANAGER', 'Warehouse Manager', 'Runs a warehouse.', TRUE),
    ('WAREHOUSE_STAFF', 'Warehouse Staff', 'Works at a warehouse.', TRUE);

-- Their starting permissions: exactly what each role could already do, plus the
-- new content-level switches (dashboard panels, costs, profit, inventory value,
-- batch choice, discounts).
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT r.id, p.permission
FROM access_roles r
JOIN (VALUES
    ('STORE_MANAGER', 'DASHBOARD_VIEW'),
    ('STORE_MANAGER', 'INVENTORY_VIEW'),
    ('STORE_MANAGER', 'INVENTORY_EDIT'),
    ('STORE_MANAGER', 'PRODUCT_CREATE'),
    ('STORE_MANAGER', 'PRODUCT_EDIT'),
    ('STORE_MANAGER', 'CATEGORY_MANAGE'),
    ('STORE_MANAGER', 'POS_ACCESS'),
    ('STORE_MANAGER', 'SALES_VIEW'),
    ('STORE_MANAGER', 'SALES_CREATE'),
    ('STORE_MANAGER', 'SALES_RETURN'),
    ('STORE_MANAGER', 'PURCHASE_CREATE'),
    ('STORE_MANAGER', 'PURCHASE_RECEIVE'),
    ('STORE_MANAGER', 'SUPPLIER_MANAGE'),
    ('STORE_MANAGER', 'TRANSFER_CREATE'),
    ('STORE_MANAGER', 'TRANSFER_APPROVE'),
    ('STORE_MANAGER', 'TRANSFER_RECEIVE'),
    ('STORE_MANAGER', 'STOCK_ADJUST'),
    ('STORE_MANAGER', 'EXPENSE_CREATE'),
    ('STORE_MANAGER', 'FINANCE_VIEW'),
    ('STORE_MANAGER', 'REPORT_VIEW'),
    ('STORE_MANAGER', 'DASHBOARD_REVENUE'),
    ('STORE_MANAGER', 'DASHBOARD_EXPENSES'),
    ('STORE_MANAGER', 'DASHBOARD_LOW_STOCK'),
    ('STORE_MANAGER', 'DASHBOARD_RECENT_SALES'),
    ('STORE_MANAGER', 'DASHBOARD_RECENT_TRANSFERS'),
    ('STORE_MANAGER', 'DASHBOARD_BRANCH_PERFORMANCE'),
    ('STORE_MANAGER', 'VIEW_COSTS'),
    ('STORE_MANAGER', 'VIEW_PROFIT'),
    ('STORE_MANAGER', 'POS_CHOOSE_BATCH'),
    ('STORE_MANAGER', 'POS_DISCOUNT'),
    ('STORE_STAFF', 'DASHBOARD_VIEW'),
    ('STORE_STAFF', 'INVENTORY_VIEW'),
    ('STORE_STAFF', 'POS_ACCESS'),
    ('STORE_STAFF', 'SALES_VIEW'),
    ('STORE_STAFF', 'SALES_CREATE'),
    ('STORE_STAFF', 'TRANSFER_CREATE'),
    ('STORE_STAFF', 'TRANSFER_RECEIVE'),
    ('STORE_STAFF', 'EXPENSE_CREATE'),
    ('STORE_STAFF', 'DASHBOARD_LOW_STOCK'),
    ('STORE_STAFF', 'DASHBOARD_RECENT_SALES'),
    ('STORE_STAFF', 'DASHBOARD_RECENT_TRANSFERS'),
    ('STORE_STAFF', 'POS_CHOOSE_BATCH'),
    ('STORE_STAFF', 'POS_DISCOUNT'),
    ('WAREHOUSE_MANAGER', 'DASHBOARD_VIEW'),
    ('WAREHOUSE_MANAGER', 'INVENTORY_VIEW'),
    ('WAREHOUSE_MANAGER', 'INVENTORY_EDIT'),
    ('WAREHOUSE_MANAGER', 'PRODUCT_CREATE'),
    ('WAREHOUSE_MANAGER', 'PRODUCT_EDIT'),
    ('WAREHOUSE_MANAGER', 'CATEGORY_MANAGE'),
    ('WAREHOUSE_MANAGER', 'PURCHASE_CREATE'),
    ('WAREHOUSE_MANAGER', 'PURCHASE_RECEIVE'),
    ('WAREHOUSE_MANAGER', 'SUPPLIER_MANAGE'),
    ('WAREHOUSE_MANAGER', 'TRANSFER_CREATE'),
    ('WAREHOUSE_MANAGER', 'TRANSFER_APPROVE'),
    ('WAREHOUSE_MANAGER', 'TRANSFER_RECEIVE'),
    ('WAREHOUSE_MANAGER', 'STOCK_ADJUST'),
    ('WAREHOUSE_MANAGER', 'EXPENSE_CREATE'),
    ('WAREHOUSE_MANAGER', 'FINANCE_VIEW'),
    ('WAREHOUSE_MANAGER', 'REPORT_VIEW'),
    ('WAREHOUSE_MANAGER', 'DASHBOARD_EXPENSES'),
    ('WAREHOUSE_MANAGER', 'DASHBOARD_LOW_STOCK'),
    ('WAREHOUSE_MANAGER', 'DASHBOARD_RECENT_TRANSFERS'),
    ('WAREHOUSE_MANAGER', 'DASHBOARD_BRANCH_PERFORMANCE'),
    ('WAREHOUSE_MANAGER', 'VIEW_COSTS'),
    ('WAREHOUSE_MANAGER', 'VIEW_INVENTORY_VALUE'),
    ('WAREHOUSE_STAFF', 'DASHBOARD_VIEW'),
    ('WAREHOUSE_STAFF', 'INVENTORY_VIEW'),
    ('WAREHOUSE_STAFF', 'PURCHASE_RECEIVE'),
    ('WAREHOUSE_STAFF', 'TRANSFER_CREATE'),
    ('WAREHOUSE_STAFF', 'TRANSFER_RECEIVE'),
    ('WAREHOUSE_STAFF', 'EXPENSE_CREATE'),
    ('WAREHOUSE_STAFF', 'DASHBOARD_LOW_STOCK'),
    ('WAREHOUSE_STAFF', 'DASHBOARD_RECENT_TRANSFERS'),
    ('SUPER_ADMIN', 'DASHBOARD_VIEW'),
    ('SUPER_ADMIN', 'DASHBOARD_REVENUE'),
    ('SUPER_ADMIN', 'DASHBOARD_EXPENSES'),
    ('SUPER_ADMIN', 'DASHBOARD_LOW_STOCK'),
    ('SUPER_ADMIN', 'DASHBOARD_RECENT_SALES'),
    ('SUPER_ADMIN', 'DASHBOARD_RECENT_TRANSFERS'),
    ('SUPER_ADMIN', 'DASHBOARD_BRANCH_PERFORMANCE'),
    ('SUPER_ADMIN', 'VIEW_COSTS'),
    ('SUPER_ADMIN', 'VIEW_PROFIT'),
    ('SUPER_ADMIN', 'VIEW_INVENTORY_VALUE'),
    ('SUPER_ADMIN', 'POS_ACCESS'),
    ('SUPER_ADMIN', 'POS_CHOOSE_BATCH'),
    ('SUPER_ADMIN', 'POS_DISCOUNT'),
    ('SUPER_ADMIN', 'SALES_VIEW'),
    ('SUPER_ADMIN', 'SALES_CREATE'),
    ('SUPER_ADMIN', 'SALES_RETURN'),
    ('SUPER_ADMIN', 'INVENTORY_VIEW'),
    ('SUPER_ADMIN', 'INVENTORY_EDIT'),
    ('SUPER_ADMIN', 'PRODUCT_CREATE'),
    ('SUPER_ADMIN', 'PRODUCT_EDIT'),
    ('SUPER_ADMIN', 'PRODUCT_DELETE'),
    ('SUPER_ADMIN', 'CATEGORY_MANAGE'),
    ('SUPER_ADMIN', 'STOCK_ADJUST'),
    ('SUPER_ADMIN', 'PURCHASE_CREATE'),
    ('SUPER_ADMIN', 'PURCHASE_RECEIVE'),
    ('SUPER_ADMIN', 'SUPPLIER_MANAGE'),
    ('SUPER_ADMIN', 'TRANSFER_CREATE'),
    ('SUPER_ADMIN', 'TRANSFER_APPROVE'),
    ('SUPER_ADMIN', 'TRANSFER_RECEIVE'),
    ('SUPER_ADMIN', 'FINANCE_VIEW'),
    ('SUPER_ADMIN', 'EXPENSE_CREATE'),
    ('SUPER_ADMIN', 'REPORT_VIEW'),
    ('SUPER_ADMIN', 'USER_MANAGE'),
    ('SUPER_ADMIN', 'BRANCH_MANAGE'),
    ('SUPER_ADMIN', 'SETTINGS_MANAGE')
) AS p (base_role, permission) ON p.base_role = r.base_role
WHERE r.system_role;

-- Every existing user points at the built-in role matching their current role.
ALTER TABLE app_users ADD COLUMN access_role_id UUID REFERENCES access_roles (id);
UPDATE app_users u
SET access_role_id = r.id
FROM access_roles r
WHERE r.system_role AND r.base_role = u.role;
ALTER TABLE app_users ALTER COLUMN access_role_id SET NOT NULL;
CREATE INDEX idx_app_users_access_role ON app_users (access_role_id);
