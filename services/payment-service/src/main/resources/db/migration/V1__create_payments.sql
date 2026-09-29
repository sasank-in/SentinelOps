CREATE TABLE payments (
    id             UUID PRIMARY KEY,
    order_id       UUID           NOT NULL UNIQUE,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    decline_reason VARCHAR(64),
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_payments_created_at ON payments (created_at);
