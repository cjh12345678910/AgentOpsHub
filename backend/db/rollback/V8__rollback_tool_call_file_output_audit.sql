DROP INDEX IF EXISTS idx_tool_call_logs_target_path;

ALTER TABLE tool_call_logs
    DROP COLUMN IF EXISTS target_path,
    DROP COLUMN IF EXISTS size_bytes;
