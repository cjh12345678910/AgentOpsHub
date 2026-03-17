ALTER TABLE tool_call_logs
    ADD COLUMN IF NOT EXISTS policy_decision VARCHAR(16),
    ADD COLUMN IF NOT EXISTS deny_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS required_scope VARCHAR(128),
    ADD COLUMN IF NOT EXISTS safety_rule VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_tool_call_logs_policy_decision ON tool_call_logs(policy_decision);
CREATE INDEX IF NOT EXISTS idx_tool_call_logs_created_policy ON tool_call_logs(created_at, policy_decision);
