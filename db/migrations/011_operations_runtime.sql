-- 运营模块运行约束、实时轮询版本与默认规则。

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;
ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;
ALTER TABLE status_requests ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_announcements_admin_time
    ON announcements(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_work_schedules_week
    ON work_schedules(week_start DESC);
CREATE INDEX IF NOT EXISTS idx_employee_current_status_overtime
    ON employee_current_statuses(expected_end_at) WHERE is_overtime = false;

UPDATE status_type_rules SET
    capacity_limit = CASE status_code WHEN 'short_break' THEN 2 WHEN 'long_break' THEN 1 ELSE capacity_limit END,
    default_duration_minutes = CASE status_code WHEN 'short_break' THEN 10 WHEN 'long_break' THEN 20 WHEN 'meal' THEN 30 WHEN 'coaching' THEN 30 WHEN 'meeting' THEN 30 ELSE default_duration_minutes END,
    minimum_duration_minutes = CASE WHEN status_code = 'working' THEN NULL ELSE 5 END,
    maximum_duration_minutes = CASE status_code WHEN 'short_break' THEN 30 WHEN 'long_break' THEN 60 WHEN 'meal' THEN 90 WHEN 'coaching' THEN 120 WHEN 'meeting' THEN 240 ELSE maximum_duration_minutes END;
