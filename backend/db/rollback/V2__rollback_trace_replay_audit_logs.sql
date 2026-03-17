DROP INDEX IF EXISTS idx_tool_call_logs_created_at;
DROP INDEX IF EXISTS idx_tool_call_logs_step_id;
DROP INDEX IF EXISTS idx_tool_call_logs_task_id;
DROP TABLE IF EXISTS tool_call_logs;

ALTER TABLE task_steps
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS error_message,
    DROP COLUMN IF EXISTS error_code;
