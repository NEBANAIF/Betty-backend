-- Global (imported) purchases with shipment tracking, and a receipt log for every delivery into a warehouse.
-- Every existing purchase order becomes LOCAL and keeps working exactly as before.

ALTER TABLE purchase_orders
    ADD COLUMN purchase_type        VARCHAR(10)    NOT NULL DEFAULT 'LOCAL'
        CONSTRAINT chk_po_purchase_type CHECK (purchase_type IN ('LOCAL', 'GLOBAL')),
    ADD COLUMN origin_country       VARCHAR(100),
    ADD COLUMN destination_country  VARCHAR(100),
    ADD COLUMN shipping_method      VARCHAR(20)
        CONSTRAINT chk_po_shipping_method CHECK (shipping_method IN ('SEA', 'AIR', 'ROAD', 'RAIL', 'MULTIMODAL')),
    -- Currency the supplier is paid in; the unit costs on a global order are in this currency.
    ADD COLUMN currency             VARCHAR(10),
    -- How many units of the local currency one unit of `currency` is worth. Received stock is costed with it.
    ADD COLUMN exchange_rate        NUMERIC(18, 6) NOT NULL DEFAULT 1
        CONSTRAINT chk_po_exchange_rate CHECK (exchange_rate > 0),
    ADD COLUMN shipping_cost        NUMERIC(14, 2)
        CONSTRAINT chk_po_shipping_cost CHECK (shipping_cost IS NULL OR shipping_cost >= 0),
    ADD COLUMN expected_arrival_date DATE,
    -- Worked out from the route whenever it is saved (see ShipmentCalculator), kept here so lists can filter on it.
    ADD COLUMN actual_arrival_date  DATE,
    ADD COLUMN shipment_status      VARCHAR(20)
        CONSTRAINT chk_po_shipment_status CHECK (shipment_status IN ('NOT_SHIPPED', 'IN_TRANSIT', 'ARRIVED')),
    ADD COLUMN current_location     VARCHAR(250),
    ADD COLUMN current_country      VARCHAR(100),
    -- Month/year of the actual arrival, or of the expected arrival until the goods have arrived.
    ADD COLUMN arrival_year         INTEGER,
    ADD COLUMN arrival_month        INTEGER
        CONSTRAINT chk_po_arrival_month CHECK (arrival_month IS NULL OR arrival_month BETWEEN 1 AND 12);

CREATE INDEX idx_po_purchase_type    ON purchase_orders (purchase_type);
CREATE INDEX idx_po_shipment_status  ON purchase_orders (shipment_status);
CREATE INDEX idx_po_arrival          ON purchase_orders (arrival_year, arrival_month);

CREATE TABLE purchase_shipment_stages (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id  UUID         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    stage_order        INTEGER      NOT NULL CHECK (stage_order > 0),
    country            VARCHAR(100) NOT NULL,
    location           VARCHAR(150),
    arrival_date       DATE,
    departure_date     DATE,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ARRIVED', 'DEPARTED', 'DELAYED')),
    notes              VARCHAR(300),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_stage_dates CHECK (arrival_date IS NULL OR departure_date IS NULL OR departure_date >= arrival_date)
);
-- Not unique on (purchase_order_id, stage_order): a route is replaced as a whole and inserts run before deletes.
CREATE INDEX idx_stage_po ON purchase_shipment_stages (purchase_order_id, stage_order);
CREATE INDEX idx_stage_country ON purchase_shipment_stages (country);

CREATE TABLE purchase_receipts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id  UUID         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    -- Made by the screen when the receive dialog opens; unique, so one delivery can never be booked twice.
    request_id         UUID,
    received_date      DATE         NOT NULL,
    received_by        UUID         NOT NULL REFERENCES app_users (id),
    note               VARCHAR(300),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_receipt_request UNIQUE (request_id)
);
CREATE INDEX idx_receipt_po ON purchase_receipts (purchase_order_id);

CREATE TABLE purchase_receipt_lines (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id               UUID    NOT NULL REFERENCES purchase_receipts (id) ON DELETE CASCADE,
    purchase_order_item_id   UUID    NOT NULL REFERENCES purchase_order_items (id),
    quantity                 INTEGER NOT NULL CHECK (quantity > 0),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_receipt_line_receipt ON purchase_receipt_lines (receipt_id);
