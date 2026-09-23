CREATE TABLE admin_sessions (
    id UUID PRIMARY KEY,
    session_token_digest TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_admin_sessions_session_token_digest UNIQUE (session_token_digest)
);

CREATE INDEX idx_admin_sessions_expires_at ON admin_sessions (expires_at);
