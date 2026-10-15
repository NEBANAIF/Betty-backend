-- Phase 6: POS & Sales. COGS/gross profit are never stored - they're
-- always derived from sale_batch_allocations at query time (see
-- SaleResponse). Loan (Phase 7) and financial_transactions (Phase 10)
-- reference sales.id but are added in later migrations.

CREATE TABLE customers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(255),
    address     VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE sales (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_number       VARCHAR(50)    NOT NULL,
    branch_id         UUID           NOT NULL REFERENCES branches (id),
    customer_id       UUID REFERENCES customers (id),
    total_amount      NUMERIC(14, 2) NOT NULL CHECK (total_amount >= 0),
    discount_amount   NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    paid_amount       NUMERIC(14, 2) NOT NULL CHECK (paid_amount >= 0),
    remaining_amount  NUMERIC(14, 2) NOT NULL CHECK (remaining_amount >= 0),
    payment_status    VARCHAR(20)    NOT NULL CHECK (payment_status IN ('PAID', 'PARTIAL', 'CREDIT')),
    status            VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'CANCELLED')),
    created_by        UUID           NOT NULL REFERENCES app_users (id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_sale_number UNIQUE (sale_number),
    -- A non-fully-paid sale must be tied to a real customer - credit
    -- cannot be extended to an anonymous walk-in.
    CONSTRAINT chk_credit_requires_customer CHECK (remaining_amount = 0 OR customer_id IS NOT NULL)
);

CREATE INDEX idx_sales_branch_created ON sales (branch_id, created_at DESC);
CREATE INDEX idx_sales_customer ON sales (customer_id);
CREATE INDEX idx_sales_payment_status ON sales (payment_status);

CREATE TABLE sale_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id     UUID           NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_id  UUID           NOT NULL REFERENCES products (id),
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    line_total  NUMERIC(14, 2) NOT NULL CHECK (line_total >= 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_sale_items_sale_id ON sale_items (sale_id);
CREATE INDEX idx_sale_items_product_id ON sale_items (product_id);

CREATE TABLE sale_batch_allocations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_item_id        UUID           NOT NULL REFERENCES sale_items (id) ON DELETE CASCADE,
    batch_id            UUID           NOT NULL REFERENCES product_batches (id),
    quantity_allocated  INTEGER        NOT NULL CHECK (quantity_allocated > 0),
    unit_cost           NUMERIC(14, 4) NOT NULL CHECK (unit_cost >= 0),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_sale_batch_alloc_sale_item ON sale_batch_allocations (sale_item_id);
CREATE INDEX idx_sale_batch_alloc_batch ON sale_batch_allocations (batch_id);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    method              VARCHAR(20)    NOT NULL CHECK (method IN ('CASH', 'BANK', 'CREDIT')),
    amount              NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    branch_id           UUID           NOT NULL REFERENCES branches (id),
    reference_type      VARCHAR(30)    NOT NULL CHECK (reference_type IN ('SALE', 'LOAN_PAYMENT', 'SUPPLIER_PAYMENT')),
    reference_id        UUID           NOT NULL,
    bank_name           VARCHAR(255),
    account_reference   VARCHAR(255),
    received_by         UUID           NOT NULL REFERENCES app_users (id),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_reference ON payments (reference_type, reference_id);
CREATE INDEX idx_payments_branch ON payments (branch_id);
