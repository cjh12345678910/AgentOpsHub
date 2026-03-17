ALTER TABLE tool_call_logs
    ADD COLUMN IF NOT EXISTS target_path VARCHAR(512),
    ADD COLUMN IF NOT EXISTS size_bytes BIGINT;

CREATE INDEX IF NOT EXISTS idx_tool_call_logs_target_path ON tool_call_logs(target_path);
