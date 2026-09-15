ALTER TABLE sessions ADD COLUMN session_token_digest TEXT;

CREATE UNIQUE INDEX uq_sessions_session_token_digest
    ON sessions (session_token_digest)
    WHERE session_token_digest IS NOT NULL;
