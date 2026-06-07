CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    holder_name VARCHAR(255) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    from_account_id UUID NOT NULL REFERENCES accounts (id),
    to_account_id UUID NOT NULL REFERENCES accounts (id),
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transfers_amount_positive CHECK (amount > 0)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts (id),
    entry_type VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    transfer_id UUID NOT NULL REFERENCES transfers (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ledger_account_created ON ledger_entries (account_id, created_at DESC);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_transfer ON notifications (transfer_id);
