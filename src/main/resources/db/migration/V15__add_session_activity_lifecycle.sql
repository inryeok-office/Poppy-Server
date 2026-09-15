ALTER TABLE sessions ADD COLUMN last_activity_at TIMESTAMPTZ;
ALTER TABLE sessions ADD COLUMN expired_at TIMESTAMPTZ;

UPDATE sessions
SET last_activity_at = created_at
WHERE last_activity_at IS NULL;

CREATE INDEX idx_sessions_expiration
    ON sessions (last_activity_at)
    WHERE expired_at IS NULL;
