-- Draft: qa_approvals table for week 2-3 QA approval flow.
-- One active valid approval per command; same operator is idempotent,
-- different operator with existing valid approval is a conflict.

CREATE TABLE qa_approvals (
    id UUID PRIMARY KEY,
    command_id UUID NOT NULL REFERENCES commands(id),
    operator_address VARCHAR(42) NOT NULL,
    approval_deadline BIGINT NOT NULL,
    signature TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT qa_approvals_status_check CHECK (status IN ('PENDING', 'USED', 'EXPIRED', 'SUPERSEDED')),
    CONSTRAINT qa_approvals_deadline_check CHECK (approval_deadline > 0)
);

CREATE INDEX idx_qa_approvals_command ON qa_approvals (command_id);
CREATE UNIQUE INDEX uq_qa_approvals_active ON qa_approvals (command_id)
    WHERE status = 'PENDING';
