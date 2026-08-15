-- 可版本化系统设置与不可隐藏、可排序的后台菜单。

CREATE TABLE IF NOT EXISTS system_settings (
    id bigserial PRIMARY KEY,
    setting_key text NOT NULL UNIQUE,
    value jsonb NOT NULL,
    description text NOT NULL,
    value_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version > 0),
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS system_setting_versions (
    id bigserial PRIMARY KEY,
    setting_id bigint NOT NULL REFERENCES system_settings(id),
    version_number integer NOT NULL CHECK (version_number > 0),
    value jsonb NOT NULL,
    change_reason text NOT NULL,
    changed_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (setting_id, version_number)
);

CREATE OR REPLACE FUNCTION prevent_system_setting_version_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '系统设置历史版本不可修改或删除';
END;
$$;

CREATE TRIGGER trg_prevent_system_setting_version_mutation
BEFORE UPDATE OR DELETE ON system_setting_versions
FOR EACH ROW EXECUTE FUNCTION prevent_system_setting_version_mutation();

CREATE TABLE IF NOT EXISTS admin_menu_items (
    id bigserial PRIMARY KEY,
    module_code text NOT NULL UNIQUE,
    display_name text NOT NULL,
    default_position integer NOT NULL UNIQUE CHECK (default_position > 0),
    route_anchor text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_menu_orders (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    menu_item_id bigint NOT NULL REFERENCES admin_menu_items(id) ON DELETE CASCADE,
    position integer NOT NULL CHECK (position > 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, menu_item_id),
    UNIQUE (user_id, position)
);

CREATE INDEX IF NOT EXISTS idx_system_setting_versions_history
    ON system_setting_versions(setting_id, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_user_menu_orders_render
    ON user_menu_orders(user_id, position);

INSERT INTO admin_menu_items(module_code, display_name, default_position, route_anchor) VALUES
    ('overview', '管理总览', 1, 'overview'),
    ('status_requests', '状态申请', 2, 'status-requests'),
    ('feedback', '待维护词与答案反馈', 3, 'feedback'),
    ('knowledge', '知识库管理', 4, 'knowledge'),
    ('announcements', '公告管理', 5, 'announcements'),
    ('schedules', '班务与值班', 6, 'schedules'),
    ('taxonomy', '类目与标签', 7, 'taxonomy'),
    ('accounts', '账号与权限', 8, 'accounts'),
    ('audit', '操作日志', 9, 'audit'),
    ('settings', '系统设置', 10, 'settings')
ON CONFLICT (module_code) DO NOTHING;

INSERT INTO system_settings(setting_key, value, description, value_schema) VALUES
    ('knowledge.expiry_warning_days', '7'::jsonb, '知识临近失效提醒天数', '{"type":"integer","minimum":1,"maximum":30}'::jsonb),
    ('shift.early_time', '"07:00-14:30"'::jsonb, '默认早班时间', '{"type":"string"}'::jsonb),
    ('shift.late_time', '"14:30-23:00"'::jsonb, '默认晚班时间', '{"type":"string"}'::jsonb)
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_setting_versions(setting_id, version_number, value, change_reason)
SELECT id, 1, value, '企业版数据库初始配置'
FROM system_settings
WHERE setting_key IN ('knowledge.expiry_warning_days', 'shift.early_time', 'shift.late_time')
ON CONFLICT (setting_id, version_number) DO NOTHING;
