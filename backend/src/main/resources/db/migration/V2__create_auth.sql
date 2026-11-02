CREATE TABLE auth_challenges (
    id UUID PRIMARY KEY,
    address VARCHAR(42) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    domain VARCHAR(253) NOT NULL,
    uri TEXT NOT NULL,
    chain_id BIGINT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT auth_challenges_chain_id_check CHECK (chain_id > 0)
);

CREATE UNIQUE INDEX uq_auth_challenges_nonce ON auth_challenges (nonce);
CREATE INDEX idx_auth_challenges_expires_at ON auth_challenges (expires_at);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    address VARCHAR(42) NOT NULL,
    chain_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    idle_timeout_seconds BIGINT NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT auth_sessions_chain_id_check CHECK (chain_id > 0),
    CONSTRAINT auth_sessions_idle_timeout_check CHECK (idle_timeout_seconds > 0)
);

CREATE UNIQUE INDEX uq_auth_sessions_token_hash ON auth_sessions (token_hash);
CREATE INDEX idx_auth_sessions_address ON auth_sessions (address);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);
