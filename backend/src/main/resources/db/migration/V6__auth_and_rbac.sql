-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Roles table
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

-- Permissions table
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    scope VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_permissions_scope ON permissions(scope);

-- User-Role association table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

-- Role-Permission association table
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Auth audit logs table
CREATE TABLE IF NOT EXISTS auth_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    resource VARCHAR(255),
    action VARCHAR(64),
    decision VARCHAR(32),
    reason TEXT,
    ip_address VARCHAR(64),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_logs_user_id ON auth_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_audit_logs_event_type ON auth_audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_auth_audit_logs_created_at ON auth_audit_logs(created_at);

-- Seed data: Insert default permissions
INSERT INTO permissions (resource, action, scope, description, created_at, updated_at) VALUES
    ('*', '*', '*', 'Full access to all resources', NOW(), NOW()),
    ('rag', 'search', 'rag:search', 'Search RAG documents', NOW(), NOW()),
    ('rag', 'chunk:read', 'rag:chunk:read', 'Read RAG chunks', NOW(), NOW()),
    ('tool', 'http_get', 'tool:http_get', 'Execute HTTP GET tool', NOW(), NOW()),
    ('sql', 'select:readonly', 'sql:select:readonly', 'Execute read-only SQL queries', NOW(), NOW());

-- Seed data: Insert default roles
INSERT INTO roles (name, description, created_at, updated_at) VALUES
    ('admin', 'Administrator with full access', NOW(), NOW()),
    ('operator', 'Operator with standard access', NOW(), NOW()),
    ('viewer', 'Viewer with read-only access', NOW(), NOW());

-- Seed data: Associate roles with permissions
-- Admin role gets all permissions (*)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'admin' AND p.scope = '*';

-- Operator role gets rag:search, rag:chunk:read, tool:http_get
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'operator' AND p.scope IN ('rag:search', 'rag:chunk:read', 'tool:http_get');

-- Viewer role gets rag:search, rag:chunk:read, sql:select:readonly
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'viewer' AND p.scope IN ('rag:search', 'rag:chunk:read', 'sql:select:readonly');

-- Seed data: Create default admin user
-- Password: admin123 (BCrypt hash with strength 10)
INSERT INTO users (username, password_hash, email, status, created_at, updated_at) VALUES
    ('admin', '$2a$10$TI0CppgUqjR3FFCuaw9HiuSb06Omq4BfjysCtbnaiYifkbNfTYfLi', 'admin@agentopshub.local', 'active', NOW(), NOW());

-- Seed data: Assign admin role to admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'admin';
