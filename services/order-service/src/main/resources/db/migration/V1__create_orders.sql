CREATE TABLE orders (
    id           UUID PRIMARY KEY,
    customer_id  VARCHAR(64)    NOT NULL,
    status       VARCHAR(32)    NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    payment_id   UUID,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);

CREATE TABLE order_items (
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku        VARCHAR(64)    NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
