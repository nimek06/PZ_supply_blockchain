CREATE TABLE commands (
    id UUID PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    type VARCHAR(16) NOT NULL,
    owner_address VARCHAR(42) NOT NULL,
    batch_id VARCHAR(66),
    batch_number VARCHAR(32),
    stage_id INTEGER,
    station_address VARCHAR(42),
    station_certificate_id VARCHAR(128),
    report_hash VARCHAR(66),
    report_json TEXT,
    report_jcs BYTEA,
    station_signature TEXT NOT NULL,
    signed_payload_digest VARCHAR(66) NOT NULL,
    deadline BIGINT NOT NULL,
    qa_passed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    outcome VARCHAR(16),
    cid TEXT,
    failure_code VARCHAR(64),
    retry_allowed_after BIGINT,
    retry_at TIMESTAMPTZ,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT commands_type_check CHECK (type IN ('CREATE_BATCH', 'STAGE')),
    CONSTRAINT commands_status_check CHECK (status IN (
        'ACCEPTED', 'PINNING', 'WAITING_FOR_QA', 'READY_TO_SUBMIT', 'SUBMITTED', 'CONFIRMED', 'FAILED'
    )),
    CONSTRAINT commands_outcome_check CHECK (outcome IS NULL OR outcome IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT commands_stage_id_check CHECK (stage_id IS NULL OR stage_id BETWEEN 0 AND 3),
    CONSTRAINT commands_deadline_check CHECK (deadline >= 0),
    CONSTRAINT commands_stage_shape_check CHECK (
        (type = 'CREATE_BATCH' AND stage_id IS NULL)
        OR (type = 'STAGE'
            AND batch_id IS NOT NULL
            AND stage_id IS NOT NULL
            AND station_address IS NOT NULL
            AND station_certificate_id IS NOT NULL
            AND report_hash IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_commands_digest
    ON commands (chain_id, contract_address, signed_payload_digest);

CREATE UNIQUE INDEX uq_commands_active_stage
    ON commands (chain_id, contract_address, batch_id, stage_id)
    WHERE type = 'STAGE'
      AND (
          status IN ('ACCEPTED', 'PINNING', 'WAITING_FOR_QA', 'READY_TO_SUBMIT', 'SUBMITTED')
          OR (status = 'CONFIRMED' AND outcome = 'ACCEPTED')
      );

CREATE INDEX idx_commands_stage_created
    ON commands (chain_id, contract_address, batch_id, stage_id, created_at DESC);

CREATE INDEX idx_commands_worker
    ON commands (status, retry_at)
    WHERE status IN ('ACCEPTED', 'PINNING', 'READY_TO_SUBMIT', 'FAILED');
