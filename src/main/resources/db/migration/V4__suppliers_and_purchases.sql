-- Phase 5: Purchasing. Receiving a purchase_order_item automatically
-- creates a product_batches row (application-level, in
-- StockMutationService) - there is no manual "create batch" table or
-- endpoint.

CREATE TABLE suppliers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(255),
    address     VARCHAR(500),
    tax_number  VARCHAR(100),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE purchase_orders (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number  VARCHAR(50)    NOT NULL,
    supplier_id   UUID           NOT NULL REFERENCES suppliers (id),
    branch_id     UUID           NOT NULL REFERENCES branches (id),
    order_date    DATE           NOT NULL,
    status        VARCHAR(30)    NOT NULL CHECK (status IN
                    ('DRAFT', 'PENDING', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    total         NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
    created_by    UUID           NOT NULL REFERENCES app_users (id),
    approved_by   UUID REFERENCES app_users (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_po_order_number UNIQUE (order_number)
);

CREATE TABLE purchase_order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id   UUID           NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id          UUID           NOT NULL REFERENCES products (id),
    quantity            INTEGER        NOT NULL CHECK (quantity > 0),
    received_quantity   INTEGER        NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    unit_cost           NUMERIC(14, 4) NOT NULL CHECK (unit_cost > 0),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_received_lte_ordered CHECK (received_quantity <= quantity)
);

CREATE INDEX idx_po_branch_id ON purchase_orders (branch_id);
CREATE INDEX idx_po_supplier_id ON purchase_orders (supplier_id);
CREATE INDEX idx_po_status ON purchase_orders (status);
CREATE INDEX idx_po_items_purchase_order_id ON purchase_order_items (purchase_order_id);
CREATE INDEX idx_po_items_product_id ON purchase_order_items (product_id);
