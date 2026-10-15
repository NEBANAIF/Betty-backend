-- Registered banks, so a bank payment names a real, managed bank instead of free text.
-- A payment keeps a snapshot of the bank's name (bank_name) so history stays readable if a
-- bank is later renamed; bank_id links it to the live record. Banks are never deleted, only
-- deactivated, because payments refer to them.
CREATE TABLE banks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(120) NOT NULL,
    account_name   VARCHAR(120),
    account_number VARCHAR(60)  NOT NULL,
    notes          VARCHAR(255),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0
);
-- The same bank can hold several accounts, but the same account is registered once.
CREATE UNIQUE INDEX uk_banks_name_account ON banks (LOWER(name), account_number);

ALTER TABLE payments ADD COLUMN bank_id UUID REFERENCES banks (id);
CREATE INDEX idx_payments_bank ON payments (bank_id);
CREATE INDEX idx_payments_created_at ON payments (created_at);

-- New permissions. The Payments page used to open for anyone with SALES_VIEW or FINANCE_VIEW;
-- every role that could open it before (built-in or custom) keeps that access as PAYMENT_VIEW.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, 'PAYMENT_VIEW'
FROM access_role_permissions
WHERE permission IN ('SALES_VIEW', 'FINANCE_VIEW')
ON CONFLICT DO NOTHING;

-- Seeing bank account numbers and registering banks: the Super Admin (stored for display; it
-- always has everything anyway) and the two manager roles can see accounts. Registering and
-- editing banks starts with the Super Admin only - grant BANK_MANAGE to others on the Roles page.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT r.id, p.permission
FROM access_roles r
JOIN (VALUES
    ('SUPER_ADMIN', 'BANK_VIEW'),
    ('SUPER_ADMIN', 'BANK_MANAGE'),
    ('STORE_MANAGER', 'BANK_VIEW'),
    ('WAREHOUSE_MANAGER', 'BANK_VIEW')
) AS p (base_role, permission) ON p.base_role = r.base_role
WHERE r.system_role
ON CONFLICT DO NOTHING;
