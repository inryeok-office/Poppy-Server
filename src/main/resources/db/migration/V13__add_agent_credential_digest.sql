ALTER TABLE agents ADD COLUMN credential_digest TEXT;

CREATE UNIQUE INDEX uq_agents_credential_digest
    ON agents (credential_digest)
    WHERE credential_digest IS NOT NULL;
