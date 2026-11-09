-- Week 2: CREATE_BATCH commands, idempotency and batch number allocation.
-- STAGE rows keep a signature and deadline; CREATE_BATCH rows do not.

ALTER TABLE commands ALTER COLUMN station_signature DROP NOT NULL;
ALTER TABLE commands ALTER COLUMN signed_payload_digest DROP NOT NULL;
ALTER TABLE commands ALTER COLUMN deadline DROP NOT NULL;

ALTER TABLE commands ADD COLUMN idempotency_key VARCHAR(128);
ALTER TABLE commands ADD COLUMN request_hash VARCHAR(66);

DROP INDEX uq_commands_digest;
CREATE UNIQUE INDEX uq_commands_digest
    ON commands (chain_id, contract_address, signed_payload_digest)
    WHERE signed_payload_digest IS NOT NULL;

CREATE UNIQUE INDEX uq_commands_create_idempotency
    ON commands (chain_id, contract_address, owner_address, idempotency_key)
    WHERE type = 'CREATE_BATCH' AND idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX uq_commands_create_batch_number
    ON commands (chain_id, contract_address, batch_number)
    WHERE type = 'CREATE_BATCH';

CREATE TABLE batch_sequences (
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    year INTEGER NOT NULL,
    next_value INTEGER NOT NULL,
    PRIMARY KEY (chain_id, contract_address, year),
    CONSTRAINT batch_sequences_year_check CHECK (year BETWEEN 2000 AND 9999),
    CONSTRAINT batch_sequences_next_value_check CHECK (next_value >= 1)
);
