CREATE TABLE chain_events (
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    block_hash VARCHAR(66) NOT NULL,
    log_index BIGINT NOT NULL,
    block_number BIGINT NOT NULL,
    transaction_index BIGINT NOT NULL,
    transaction_hash VARCHAR(66) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    batch_id VARCHAR(66) NOT NULL,
    command_id UUID REFERENCES commands(id) ON DELETE SET NULL,
    payload JSONB NOT NULL,
    canonical BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (chain_id, contract_address, block_hash, log_index),
    CONSTRAINT chain_events_position_check CHECK (
        block_number >= 0 AND transaction_index >= 0 AND log_index >= 0)
);

CREATE UNIQUE INDEX uq_chain_events_canonical_position
    ON chain_events (chain_id, contract_address, block_number, transaction_index, log_index)
    WHERE canonical;
CREATE INDEX idx_chain_events_batch
    ON chain_events (chain_id, contract_address, batch_id, block_number, transaction_index, log_index)
    WHERE canonical;
CREATE INDEX idx_chain_events_command
    ON chain_events (command_id)
    WHERE command_id IS NOT NULL AND canonical;

CREATE TABLE indexer_checkpoints (
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    stream VARCHAR(32) NOT NULL,
    scanned_block_number BIGINT NOT NULL,
    scanned_block_hash VARCHAR(66) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (chain_id, contract_address, stream),
    CONSTRAINT indexer_checkpoints_block_check CHECK (scanned_block_number >= 0)
);

CREATE TABLE indexer_block_anchors (
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    block_number BIGINT NOT NULL,
    block_hash VARCHAR(66) NOT NULL,
    PRIMARY KEY (chain_id, contract_address, block_number),
    CONSTRAINT indexer_block_anchors_block_check CHECK (block_number >= 0)
);

CREATE TABLE batch_projections (
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    batch_id VARCHAR(66) NOT NULL,
    batch_number VARCHAR(32) NOT NULL,
    next_stage INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_event_block BIGINT NOT NULL,
    last_event_hash VARCHAR(66) NOT NULL,
    PRIMARY KEY (chain_id, contract_address, batch_id),
    UNIQUE (chain_id, contract_address, batch_number),
    CONSTRAINT batch_projections_stage_check CHECK (next_stage BETWEEN 0 AND 4)
);

CREATE TABLE stage_projections (
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    batch_id VARCHAR(66) NOT NULL,
    stage_id INTEGER NOT NULL,
    report_hash VARCHAR(66) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    station_address VARCHAR(42) NOT NULL,
    qa_operator VARCHAR(42),
    cid TEXT NOT NULL,
    transaction_hash VARCHAR(66) NOT NULL,
    log_index BIGINT NOT NULL,
    block_number BIGINT NOT NULL,
    block_hash VARCHAR(66) NOT NULL,
    PRIMARY KEY (chain_id, contract_address, batch_id, stage_id, report_hash),
    CONSTRAINT stage_projections_stage_check CHECK (stage_id BETWEEN 0 AND 3),
    CONSTRAINT stage_projections_outcome_check CHECK (outcome IN ('COMPLETED', 'REJECTED'))
);
