-- 既有账号补齐默认工作状态；新账号由业务建号事务同步写入。
INSERT INTO employee_current_statuses(user_id,status_code,started_at,updated_at)
SELECT id,'working',now(),now() FROM users
ON CONFLICT(user_id) DO NOTHING;
