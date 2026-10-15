-- Phase 9: Customer & Supplier Returns (spec sections 21/22).
--
-- Customer returns reference a SaleBatchAllocation directly, not a bare
-- (sale_item_id, batch_id) pair - a SaleItem can straddle multiple source
-- batches (FIFO), so pinpointing the exact allocation line is what lets a
-- return restock the EXACT batch it came from and refund at that line's
-- original unit_price. See CustomerReturnItem for the full rationale.
--
-- Supplier returns mirror Sale/Transfer's item+allocation split for the
-- same reason: a requested return quantity against one purchase order line
-- can, in principle, be satisfied by the FIFO consumption service across
-- whatever batches currently hold that stock, so the allocation table is
-- what preserves per-batch cost for the value credited back.
CREATE TABLE customer_returns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id         UUID           NOT NULL REFERENCES sales (id),
    customer_id     UUID REFERENCES customers (id),
    branch_id       UUID           NOT NULL REFERENCES branches (id),
    status          VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'VOID')),
    processed_by    UUID           NOT NULL REFERENCES app_users (id),
    refund_amount   NUMERIC(14, 2) NOT NULL CHECK (refund_amount >= 0),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_customer_returns_sale ON customer_returns (sale_id);
CREATE INDEX idx_customer_returns_branch ON customer_returns (branch_id);
CREATE INDEX idx_customer_returns_customer ON customer_returns (customer_id);

CREATE TABLE customer_return_items (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_return_id      UUID           NOT NULL REFERENCES customer_returns (id) ON DELETE CASCADE,
    sale_batch_allocation_id UUID          NOT NULL REFERENCES sale_batch_allocations (id),
    quantity_returned        INTEGER       NOT NULL CHECK (quantity_returned > 0),
    -- true: quantity went back into sellable inventory (its original
    -- batch). false: written off (damaged/defective) - no stock movement
    -- happens for that portion, only the refund is recorded.
    restocked                BOOLEAN       NOT NULL DEFAULT TRUE,
    refund_amount            NUMERIC(14, 2) NOT NULL CHECK (refund_amount >= 0),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                   BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_customer_return_items_return ON customer_return_items (customer_return_id);
CREATE INDEX idx_customer_return_items_allocation ON customer_return_items (sale_batch_allocation_id);

CREATE TABLE supplier_returns (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID        NOT NULL REFERENCES purchase_orders (id),
    supplier_id       UUID        NOT NULL REFERENCES suppliers (id),
    branch_id         UUID        NOT NULL REFERENCES branches (id),
    status            VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'VOID')),
    processed_by      UUID        NOT NULL REFERENCES app_users (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_supplier_returns_po ON supplier_returns (purchase_order_id);
CREATE INDEX idx_supplier_returns_supplier ON supplier_returns (supplier_id);
CREATE INDEX idx_supplier_returns_branch ON supplier_returns (branch_id);

CREATE TABLE supplier_return_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_return_id     UUID    NOT NULL REFERENCES supplier_returns (id) ON DELETE CASCADE,
    purchase_order_item_id UUID    NOT NULL REFERENCES purchase_order_items (id),
    quantity               INTEGER NOT NULL CHECK (quantity > 0),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                BIGINT  NOT NULL DEFAULT 0
);

CREATE INDEX idx_supplier_return_items_return ON supplier_return_items (supplier_return_id);
CREATE INDEX idx_supplier_return_items_po_item ON supplier_return_items (purchase_order_item_id);

CREATE TABLE supplier_return_allocations (
    id                     UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_return_item_id UUID          NOT NULL REFERENCES supplier_return_items (id) ON DELETE CASCADE,
    batch_id               UUID           NOT NULL REFERENCES product_batches (id),
    quantity_allocated     INTEGER        NOT NULL CHECK (quantity_allocated > 0),
    unit_cost              NUMERIC(14, 4) NOT NULL CHECK (unit_cost >= 0),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_supplier_return_alloc_item ON supplier_return_allocations (supplier_return_item_id);
CREATE INDEX idx_supplier_return_alloc_batch ON supplier_return_allocations (batch_id);
