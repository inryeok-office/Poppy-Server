CREATE TABLE agents (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    agent_version TEXT NOT NULL,
    sdk_version TEXT NOT NULL,
    platform TEXT NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_agents_name
    ON agents (name);

DROP INDEX uq_robots_agent_id;
