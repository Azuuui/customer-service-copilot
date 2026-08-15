-- 企业身份、RBAC、会话和统一审计基础。

CREATE TABLE IF NOT EXISTS users (
    id bigserial PRIMARY KEY,
    corp_id text NOT NULL,
    dingtalk_user_id text NOT NULL,
    union_id text,
    name text NOT NULL,
    avatar_url text,
    department_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    last_login_at timestamptz,
    disabled_at timestamptz,
    disabled_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (corp_id, dingtalk_user_id),
    CHECK (jsonb_typeof(department_ids) = 'array')
);

CREATE TABLE IF NOT EXISTS roles (
    id bigserial PRIMARY KEY,
    code text NOT NULL UNIQUE,
    name text NOT NULL,
    is_system boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (code IN ('customer_service', 'supervisor', 'knowledge_admin', 'system_admin'))
);

CREATE TABLE IF NOT EXISTS permissions (
    id bigserial PRIMARY KEY,
    module_code text NOT NULL,
    permission_code text NOT NULL CHECK (permission_code IN ('view', 'edit', 'export')),
    name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (module_code, permission_code)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id bigint NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_by bigint REFERENCES users(id),
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id bigint NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id bigint NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token_hash text NOT NULL UNIQUE,
    login_method text NOT NULL CHECK (login_method IN ('dingtalk_sso', 'dingtalk_qr')),
    expires_at timestamptz NOT NULL,
    invalidated_at timestamptz,
    invalidated_reason text,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id bigserial PRIMARY KEY,
    actor_user_id bigint REFERENCES users(id),
    module_code text NOT NULL,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id text,
    result text NOT NULL CHECK (result IN ('success', 'failure')),
    change_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
    entry_point text,
    request_id text,
    ip_address inet,
    user_agent text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(change_summary) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id, user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_active
    ON user_sessions(user_id, expires_at) WHERE invalidated_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_logs_module_time ON audit_logs(module_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_time ON audit_logs(actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target ON audit_logs(target_type, target_id, created_at DESC);

INSERT INTO roles(code, name) VALUES
    ('customer_service', '客服'),
    ('supervisor', '主管/调度'),
    ('knowledge_admin', '知识管理员'),
    ('system_admin', '系统管理员')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions(module_code, permission_code, name)
SELECT module_code, permission_code, module_code || ':' || permission_code
FROM unnest(ARRAY[
    'overview', 'status_requests', 'feedback', 'knowledge', 'announcements',
    'schedules', 'taxonomy', 'accounts', 'audit', 'settings'
]) AS modules(module_code)
CROSS JOIN unnest(ARRAY['view', 'edit', 'export']) AS permission_codes(permission_code)
ON CONFLICT (module_code, permission_code) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles CROSS JOIN permissions
WHERE roles.code = 'system_admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles JOIN permissions ON permissions.module_code IN ('overview', 'status_requests', 'schedules')
WHERE roles.code = 'supervisor' AND permissions.permission_code IN ('view', 'edit')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles JOIN permissions ON permissions.module_code IN ('overview', 'feedback', 'knowledge', 'taxonomy')
WHERE roles.code = 'knowledge_admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;
