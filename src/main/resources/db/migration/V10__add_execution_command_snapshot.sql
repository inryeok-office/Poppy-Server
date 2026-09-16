ALTER TABLE executions ADD COLUMN compiled_command_payload TEXT;
ALTER TABLE executions ADD COLUMN required_capabilities JSONB;
