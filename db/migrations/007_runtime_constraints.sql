-- 运行环境契约：开发模拟登录和生产登录共用正式会话模型。
ALTER TABLE user_sessions DROP CONSTRAINT IF EXISTS user_sessions_login_method_check;
ALTER TABLE user_sessions ADD CONSTRAINT user_sessions_login_method_check
    CHECK (login_method IN ('mock', 'dingtalk_sso', 'dingtalk_qr'));

CREATE INDEX IF NOT EXISTS idx_users_dingtalk_identity
    ON users(corp_id, dingtalk_user_id);
