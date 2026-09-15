ALTER TABLE sessions ADD COLUMN recovery_code_digest VARCHAR(255);

CREATE UNIQUE INDEX uk_sessions_recovery_code_digest
    ON sessions (recovery_code_digest)
    WHERE recovery_code_digest IS NOT NULL;
