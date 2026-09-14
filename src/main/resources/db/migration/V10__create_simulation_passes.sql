CREATE TABLE simulation_passes (
    session_id UUID NOT NULL,
    block_version BIGINT NOT NULL,
    passed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, block_version),
    CONSTRAINT fk_simulation_passes_session
        FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_simulation_passes_revision
        FOREIGN KEY (session_id, block_version)
        REFERENCES block_revisions(session_id, version)
);
