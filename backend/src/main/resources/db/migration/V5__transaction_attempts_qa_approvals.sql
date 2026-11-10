-- Week 2: transaction attempts and QA approval extensions.

CREATE TABLE transaction_attempts (
    id UUID PRIMARY KEY,
    command_id UUID NOT NULL REFERENCES commands(id),
    relayer_address VARCHAR(42) NOT NULL,
    tx_nonce NUMERIC(78,0) NOT NULL,
    tx_hash VARCHAR(66),
    signed_tx TEXT NOT NULL,
    calldata_hash VARCHAR(66) NOT NULL,
    max_priority_fee_per_gas NUMERIC(78,0) NOT NULL,
    max_fee_per_gas NUMERIC(78,0) NOT NULL,
    gas_limit NUMERIC(78,0),
    broadcast_at TIMESTAMPTZ,
    receipt_block BIGINT,
    receipt_block_hash VARCHAR(66),
    receipt_status VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transaction_attempts_nonce_check CHECK (tx_nonce >= 0),
    CONSTRAINT transaction_attempts_fees_check CHECK (
        max_priority_fee_per_gas >= 0 AND max_fee_per_gas >= 0),
    CONSTRAINT transaction_attempts_gas_check CHECK (gas_limit IS NULL OR gas_limit >= 0),
    CONSTRAINT transaction_attempts_receipt_block_check CHECK (receipt_block IS NULL OR receipt_block >= 0),
    CONSTRAINT transaction_attempts_receipt_status_check CHECK (
        receipt_status IS NULL OR receipt_status IN ('SUCCESS', 'REVERTED'))
);

CREATE UNIQUE INDEX uq_transaction_attempts_tx_hash
    ON transaction_attempts (tx_hash)
    WHERE tx_hash IS NOT NULL;
CREATE INDEX idx_transaction_attempts_command
    ON transaction_attempts (command_id, created_at DESC);

CREATE INDEX idx_qa_approvals_command_status
    ON qa_approvals (command_id, status);
CREATE UNIQUE INDEX uq_qa_approvals_pending_operator
    ON qa_approvals (command_id, operator_address)
    WHERE status = 'PENDING';
