CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    current_block_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE block_revisions (
    session_id UUID NOT NULL REFERENCES sessions(id),
    version BIGINT NOT NULL,
    document JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, version)
);
