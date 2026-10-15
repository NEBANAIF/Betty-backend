-- Phase 4: the inventory core. product_batches is the source of truth for
-- stock and cost; inventory is a cached branch-level aggregate kept in
-- sync with it; stock_history is the append-only ledger of every movement.

CREATE TABLE product_batches (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_number      VARCHAR(100)   NOT NULL,
    product_id        UUID           NOT NULL REFERENCES products (id),
    branch_id         UUID           NOT NULL REFERENCES branches (id),
    quantity          INTEGER        NOT NULL CHECK (quantity > 0),
    remaining_quantity INTEGER       NOT NULL CHECK (remaining_quantity >= 0),
    cost_price        NUMERIC(14, 4) NOT NULL CHECK (cost_price >= 0),
    received_date     DATE           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_batch_number UNIQUE (batch_number),
    CONSTRAINT chk_remaining_lte_quantity CHECK (remaining_quantity <= quantity)
);

CREATE INDEX idx_batches_product_branch_fifo
    ON product_batches (product_id, branch_id, received_date, created_at)
    WHERE remaining_quantity > 0;

CREATE INDEX idx_batches_branch_id ON product_batches (branch_id);

CREATE TABLE inventory (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id        UUID        NOT NULL REFERENCES products (id),
    branch_id         UUID        NOT NULL REFERENCES branches (id),
    quantity          INTEGER     NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved_quantity INTEGER     NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_product_branch UNIQUE (product_id, branch_id),
    CONSTRAINT chk_reserved_lte_quantity CHECK (reserved_quantity <= quantity)
);

CREATE INDEX idx_inventory_branch_id ON inventory (branch_id);
CREATE INDEX idx_inventory_product_id ON inventory (product_id);

CREATE TABLE stock_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id        UUID        NOT NULL REFERENCES products (id),
    branch_id         UUID        NOT NULL REFERENCES branches (id),
    batch_id          UUID REFERENCES product_batches (id),
    movement_type     VARCHAR(30) NOT NULL CHECK (movement_type IN
                        ('PURCHASE', 'SALE', 'TRANSFER_OUT', 'TRANSFER_IN',
                         'CUSTOMER_RETURN', 'SUPPLIER_RETURN', 'ADJUSTMENT', 'DAMAGED', 'LOST')),
    quantity_change   INTEGER     NOT NULL,
    previous_quantity INTEGER     NOT NULL,
    new_quantity      INTEGER     NOT NULL,
    reason            VARCHAR(500),
    user_id           UUID        NOT NULL REFERENCES app_users (id),
    reference_type    VARCHAR(30) CHECK (reference_type IN
                        ('PURCHASE_ORDER', 'SALE', 'TRANSFER', 'CUSTOMER_RETURN',
                         'SUPPLIER_RETURN', 'MANUAL_ADJUSTMENT')),
    reference_id      UUID,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_stock_history_branch_occurred ON stock_history (branch_id, occurred_at DESC);
CREATE INDEX idx_stock_history_product ON stock_history (product_id);
CREATE INDEX idx_stock_history_batch ON stock_history (batch_id);
CREATE INDEX idx_stock_history_reference ON stock_history (reference_type, reference_id);
