ALTER TABLE executions
    ADD COLUMN session_id UUID REFERENCES sessions(id),
    ADD COLUMN block_version BIGINT,
    ADD COLUMN queued_at TIMESTAMPTZ;

ALTER TABLE executions
    ADD CONSTRAINT executions_provenance_consistent
    CHECK (
        (session_id IS NULL AND block_version IS NULL AND queued_at IS NULL)
        OR (session_id IS NOT NULL AND block_version IS NOT NULL AND queued_at IS NOT NULL)
    );

ALTER TABLE executions
    ADD CONSTRAINT fk_executions_block_revision
    FOREIGN KEY (session_id, block_version)
    REFERENCES block_revisions(session_id, version);
