-- Phase 8: Stock Transfers. Stock only ever moves at two points in this
-- table's lifecycle - dispatch (IN_TRANSIT) and receive (RECEIVED) - see
-- StockTransferService. stock_transfer_allocations is what lets a
-- transfer preserve exact per-batch cost from source to destination even
-- when a single transfer line is fulfilled from multiple source batches
-- at different costs.

CREATE TABLE stock_transfers (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_number        VARCHAR(50) NOT NULL,
    source_branch_id       UUID        NOT NULL REFERENCES branches (id),
    destination_branch_id  UUID        NOT NULL REFERENCES branches (id),
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN
                             ('PENDING', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'REJECTED', 'CANCELLED')),
    requested_by           UUID        NOT NULL REFERENCES app_users (id),
    approved_by            UUID REFERENCES app_users (id),
    received_by            UUID REFERENCES app_users (id),
    requested_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at            TIMESTAMPTZ,
    dispatched_at          TIMESTAMPTZ,
    received_at            TIMESTAMPTZ,
    rejection_reason       VARCHAR(500),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfer_number UNIQUE (transfer_number),
    CONSTRAINT chk_transfer_branches_differ CHECK (source_branch_id != destination_branch_id)
);

CREATE INDEX idx_transfers_source ON stock_transfers (source_branch_id);
CREATE INDEX idx_transfers_destination ON stock_transfers (destination_branch_id);
CREATE INDEX idx_transfers_status ON stock_transfers (status);

CREATE TABLE stock_transfer_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id  UUID    NOT NULL REFERENCES stock_transfers (id) ON DELETE CASCADE,
    product_id   UUID    NOT NULL REFERENCES products (id),
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT  NOT NULL DEFAULT 0
);

CREATE INDEX idx_transfer_items_transfer ON stock_transfer_items (transfer_id);

CREATE TABLE stock_transfer_allocations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_item_id    UUID           NOT NULL REFERENCES stock_transfer_items (id) ON DELETE CASCADE,
    source_batch_id     UUID           NOT NULL REFERENCES product_batches (id),
    quantity_allocated  INTEGER        NOT NULL CHECK (quantity_allocated > 0),
    unit_cost           NUMERIC(14, 4) NOT NULL CHECK (unit_cost >= 0),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_transfer_alloc_item ON stock_transfer_allocations (transfer_item_id);
CREATE INDEX idx_transfer_alloc_batch ON stock_transfer_allocations (source_batch_id);
