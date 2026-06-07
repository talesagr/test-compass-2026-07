CREATE TABLE transfer_idempotency (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    transfer_id UUID REFERENCES transfers (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transfer_idempotency_amount_positive CHECK (amount > 0)
);
