-- Phase 3: global Product/Category tables. Deliberately no branch_id,
-- stock, or branch_stock column on products - stock lives only in
-- inventory/product_batches, added in Phase 4.

CREATE TABLE categories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(1000),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_category_name UNIQUE (name)
);

CREATE TABLE products (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255)   NOT NULL,
    sku            VARCHAR(100)   NOT NULL,
    description    VARCHAR(1000),
    category_id    UUID           NOT NULL REFERENCES categories (id),
    unit           VARCHAR(50)    NOT NULL,
    selling_price  NUMERIC(14, 2) NOT NULL CHECK (selling_price >= 0),
    reorder_level  INTEGER        NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_sku UNIQUE (sku)
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_active ON products (active);
CREATE INDEX idx_products_name ON products (LOWER(name));
