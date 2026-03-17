ALTER TABLE tasks
    DROP COLUMN IF EXISTS doc_ids_json,
    DROP COLUMN IF EXISTS dispatch_status,
    DROP COLUMN IF EXISTS message_id,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS enqueued_at,
    DROP COLUMN IF EXISTS dlq_reason,
    DROP COLUMN IF EXISTS last_error_code;
