-- Manual stock changes (Add Batch, Remove stock, a new product's initial stock) are held until
-- someone approves them: nothing moves while a request is PENDING, approving it moves the stock.

CREATE TABLE stock_change_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_type   VARCHAR(20)    NOT NULL CHECK (change_type IN ('ADD_BATCH', 'REMOVE_STOCK')),
    product_id    UUID           NOT NULL REFERENCES products (id),
    branch_id     UUID           NOT NULL REFERENCES branches (id),
    quantity      INTEGER        NOT NULL CHECK (quantity > 0),
    cost_price    NUMERIC(14, 4) CHECK (cost_price >= 0),
    received_date DATE,
    movement_type VARCHAR(30),
    batch_id      UUID REFERENCES product_batches (id),
    note          VARCHAR(500),
    status        VARCHAR(10)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by  UUID           NOT NULL REFERENCES app_users (id),
    reviewed_by   UUID REFERENCES app_users (id),
    reviewed_at   TIMESTAMPTZ,
    review_note   VARCHAR(500),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_stock_change_requests_branch_status ON stock_change_requests (branch_id, status, created_at DESC);

-- A held stock change's notification can end up rejected as well as approved.
ALTER TABLE notifications DROP CONSTRAINT notifications_review_status_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_review_status_check
    CHECK (review_status IN ('PENDING', 'ON_HOLD', 'APPROVED', 'REJECTED'));

-- New permission STOCK_APPROVE. Roles that could already adjust stock keep their power to make
-- stock changes count, so they get it; give it to other roles on Roles & Permissions.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, 'STOCK_APPROVE'
FROM access_role_permissions
WHERE permission = 'STOCK_ADJUST'
ON CONFLICT DO NOTHING;
