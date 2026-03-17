INSERT INTO permissions (resource, action, scope, description, created_at, updated_at)
VALUES ('tool', 'file_write', 'tool:file_write', 'Execute file write tool', NOW(), NOW())
ON CONFLICT (scope) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.scope = 'tool:file_write'
WHERE r.name = 'operator'
ON CONFLICT (role_id, permission_id) DO NOTHING;
