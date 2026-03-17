ALTER TABLE task_steps
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tool_call_logs (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    step_id BIGINT NOT NULL,
    call_order INT NOT NULL DEFAULT 1,
    tool_name VARCHAR(64) NOT NULL,
    request_summary TEXT,
    response_summary TEXT,
    success BOOLEAN NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_tool_call_logs_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_call_logs_step FOREIGN KEY (step_id) REFERENCES task_steps(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tool_call_logs_task_id ON tool_call_logs(task_id);
CREATE INDEX IF NOT EXISTS idx_tool_call_logs_step_id ON tool_call_logs(step_id);
CREATE INDEX IF NOT EXISTS idx_tool_call_logs_created_at ON tool_call_logs(created_at);
