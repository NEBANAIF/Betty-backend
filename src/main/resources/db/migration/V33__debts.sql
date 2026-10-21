-- The business's OWN credits and loans (money it owes), separate from customer loans (money owed to it).
CREATE TABLE debts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lender_name      VARCHAR(200)   NOT NULL,
    debt_type        VARCHAR(20)    NOT NULL CHECK (debt_type IN ('BANK_LOAN', 'SUPPLIER_CREDIT', 'PERSONAL', 'OTHER')),
    description      VARCHAR(500),
    principal_amount NUMERIC(14, 2) NOT NULL CHECK (principal_amount > 0),
    interest_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (interest_amount >= 0),
    total_amount     NUMERIC(14, 2) NOT NULL CHECK (total_amount > 0),
    paid_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    remaining_amount NUMERIC(14, 2) NOT NULL CHECK (remaining_amount >= 0),
    start_date       DATE           NOT NULL,
    due_date         DATE,
    status           VARCHAR(20)    NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID')),
    notes            VARCHAR(1000),
    created_by       UUID           NOT NULL REFERENCES app_users (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_debts_status_due ON debts (status, due_date);

CREATE TABLE debt_payments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debt_id      UUID           NOT NULL REFERENCES debts (id),
    amount       NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    payment_date DATE           NOT NULL,
    method       VARCHAR(20)    NOT NULL CHECK (method IN ('CASH', 'BANK', 'CREDIT')),
    reference    VARCHAR(255),
    note         VARCHAR(500),
    created_by   UUID           NOT NULL REFERENCES app_users (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_debt_payments_debt ON debt_payments (debt_id);

-- Roles are stored in the database, so the Super Admin needs the two new switches granted here
-- (every other role gets nothing until a Super Admin ticks them on the Roles page).
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT r.id, p.permission
FROM access_roles r
JOIN (VALUES
    ('SUPER_ADMIN', 'DEBT_VIEW'),
    ('SUPER_ADMIN', 'DEBT_MANAGE')
) AS p (base_role, permission) ON p.base_role = r.base_role
WHERE r.system_role
ON CONFLICT DO NOTHING;
