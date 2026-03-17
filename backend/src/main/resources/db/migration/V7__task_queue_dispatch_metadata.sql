ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS doc_ids_json TEXT,
    ADD COLUMN IF NOT EXISTS dispatch_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS enqueued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dlq_reason TEXT,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64);

UPDATE tasks
SET dispatch_status = COALESCE(dispatch_status,
    CASE status
        WHEN 'CREATED' THEN 'CREATED'
        WHEN 'RUNNING' THEN 'RUNNING'
        WHEN 'SUCCEEDED' THEN 'SUCCEEDED'
        WHEN 'FAILED' THEN 'FAILED'
        WHEN 'CANCELLED' THEN 'CANCELLED'
        ELSE 'CREATED'
    END);

ALTER TABLE tasks
    ALTER COLUMN dispatch_status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tasks_dispatch_status ON tasks(dispatch_status);
CREATE INDEX IF NOT EXISTS idx_tasks_message_id ON tasks(message_id);
CREATE INDEX IF NOT EXISTS idx_tasks_retry_count ON tasks(retry_count);
