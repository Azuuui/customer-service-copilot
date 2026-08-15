-- 公告图片、班务代班、状态规则、独立队列与完整历史。

CREATE TABLE IF NOT EXISTS announcements (
    id bigserial PRIMARY KEY,
    title text NOT NULL,
    content text NOT NULL,
    content_format text NOT NULL DEFAULT 'plain'
        CHECK (content_format IN ('plain', 'basic_rich_text')),
    publication_status text NOT NULL DEFAULT 'draft'
        CHECK (publication_status IN ('draft', 'scheduled', 'published', 'withdrawn')),
    is_pinned boolean NOT NULL DEFAULT false,
    publish_at timestamptz,
    expire_at timestamptz,
    published_at timestamptz,
    withdrawn_at timestamptz,
    created_by bigint REFERENCES users(id),
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expire_at IS NULL OR publish_at IS NULL OR expire_at > publish_at)
);

CREATE TABLE IF NOT EXISTS announcement_images (
    id bigserial PRIMARY KEY,
    announcement_id bigint NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    object_key text NOT NULL UNIQUE,
    original_filename text NOT NULL,
    mime_type text NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png')),
    byte_size integer NOT NULL CHECK (byte_size > 0),
    CHECK (byte_size <= 5242880),
    width integer CHECK (width IS NULL OR width > 0),
    height integer CHECK (height IS NULL OR height > 0),
    sort_order smallint NOT NULL CHECK (sort_order BETWEEN 1 AND 5),
    uploaded_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (announcement_id, sort_order)
);

CREATE OR REPLACE FUNCTION enforce_announcement_image_limit()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM announcements WHERE id = NEW.announcement_id FOR UPDATE;
    IF (SELECT count(*) FROM announcement_images WHERE announcement_id = NEW.announcement_id) >= 5 THEN
        RAISE EXCEPTION '每条公告最多 5 张图片';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_enforce_announcement_image_limit
BEFORE INSERT ON announcement_images
FOR EACH ROW EXECUTE FUNCTION enforce_announcement_image_limit();

CREATE TABLE IF NOT EXISTS work_schedules (
    id bigserial PRIMARY KEY,
    week_start date NOT NULL UNIQUE,
    schedule_status text NOT NULL DEFAULT 'draft'
        CHECK (schedule_status IN ('draft', 'published', 'archived')),
    copied_from_schedule_id bigint REFERENCES work_schedules(id),
    published_at timestamptz,
    published_by bigint REFERENCES users(id),
    created_by bigint REFERENCES users(id),
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (extract(isodow FROM week_start) = 1)
);

CREATE TABLE IF NOT EXISTS shift_assignments (
    id bigserial PRIMARY KEY,
    work_schedule_id bigint NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    shift_date date NOT NULL,
    shift_code text NOT NULL CHECK (shift_code IN ('early', 'late')),
    starts_at time NOT NULL,
    ends_at time NOT NULL,
    user_id bigint NOT NULL REFERENCES users(id),
    is_dispatcher boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (work_schedule_id, shift_date, shift_code, user_id)
);

CREATE TABLE IF NOT EXISTS shift_substitutions (
    id bigserial PRIMARY KEY,
    shift_assignment_id bigint NOT NULL REFERENCES shift_assignments(id),
    substitute_user_id bigint NOT NULL REFERENCES users(id),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    reason text,
    created_by bigint REFERENCES users(id),
    ended_at timestamptz,
    ended_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);

CREATE TABLE IF NOT EXISTS status_type_rules (
    id bigserial PRIMARY KEY,
    status_code text NOT NULL UNIQUE
        CHECK (status_code IN ('working', 'short_break', 'long_break', 'meal', 'coaching', 'meeting')),
    display_name text NOT NULL,
    queue_name text,
    requires_approval boolean NOT NULL DEFAULT true,
    capacity_limit integer CHECK (capacity_limit IS NULL OR capacity_limit >= 0),
    default_duration_minutes integer CHECK (default_duration_minutes IS NULL OR default_duration_minutes > 0),
    minimum_duration_minutes integer CHECK (minimum_duration_minutes IS NULL OR minimum_duration_minutes > 0),
    maximum_duration_minutes integer CHECK (maximum_duration_minutes IS NULL OR maximum_duration_minutes > 0),
    is_active boolean NOT NULL DEFAULT true,
    updated_by bigint REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (queue_name IS NULL OR queue_name IN ('short_break', 'long_break')),
    CHECK (
        (status_code IN ('short_break', 'long_break') AND queue_name = status_code)
        OR (status_code NOT IN ('short_break', 'long_break') AND queue_name IS NULL)
    ),
    CHECK (
        minimum_duration_minutes IS NULL OR maximum_duration_minutes IS NULL
        OR minimum_duration_minutes <= maximum_duration_minutes
    ),
    CHECK (
        default_duration_minutes IS NULL OR minimum_duration_minutes IS NULL
        OR default_duration_minutes >= minimum_duration_minutes
    ),
    CHECK (
        default_duration_minutes IS NULL OR maximum_duration_minutes IS NULL
        OR default_duration_minutes <= maximum_duration_minutes
    )
);

CREATE TABLE IF NOT EXISTS status_requests (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    status_type_rule_id bigint NOT NULL REFERENCES status_type_rules(id),
    queue_name text CHECK (queue_name IS NULL OR queue_name IN ('short_break', 'long_break')),
    request_status text NOT NULL DEFAULT 'pending'
        CHECK (request_status IN ('pending', 'approved', 'rejected', 'active', 'ended', 'cancelled')),
    requested_duration_minutes integer NOT NULL CHECK (requested_duration_minutes > 0),
    queued_at timestamptz,
    position_override integer CHECK (position_override IS NULL OR position_override > 0),
    position_override_reason text,
    is_over_capacity boolean NOT NULL DEFAULT false,
    over_capacity_reason text,
    requested_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz,
    decided_by bigint REFERENCES users(id),
    decision_reason text,
    started_at timestamptz,
    expected_end_at timestamptz,
    ended_at timestamptz,
    ended_by bigint REFERENCES users(id),
    end_reason text,
    created_by_admin boolean NOT NULL DEFAULT false,
    CHECK ((queue_name IS NULL AND queued_at IS NULL) OR (queue_name IS NOT NULL AND queued_at IS NOT NULL)),
    CHECK (expected_end_at IS NULL OR started_at IS NULL OR expected_end_at > started_at),
    CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
);

CREATE TABLE IF NOT EXISTS employee_current_statuses (
    user_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    status_code text NOT NULL DEFAULT 'working'
        CHECK (status_code IN ('working', 'short_break', 'long_break', 'meal', 'coaching', 'meeting')),
    status_request_id bigint UNIQUE REFERENCES status_requests(id),
    started_at timestamptz NOT NULL DEFAULT now(),
    expected_end_at timestamptz,
    is_overtime boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS employee_status_history (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    status_request_id bigint REFERENCES status_requests(id),
    status_code text NOT NULL,
    started_at timestamptz NOT NULL,
    expected_end_at timestamptz,
    ended_at timestamptz,
    ended_by bigint REFERENCES users(id),
    end_reason text,
    was_overtime boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE TABLE IF NOT EXISTS status_request_events (
    id bigserial PRIMARY KEY,
    status_request_id bigint NOT NULL REFERENCES status_requests(id),
    actor_user_id bigint REFERENCES users(id),
    event_type text NOT NULL
        CHECK (event_type IN ('requested', 'reordered', 'approved', 'rejected', 'started', 'overtime', 'ended', 'cancelled')),
    event_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(event_data) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_announcements_visible
    ON announcements(publication_status, is_pinned DESC, publish_at, expire_at);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_lookup
    ON shift_assignments(shift_date, shift_code, is_dispatcher DESC);
CREATE INDEX IF NOT EXISTS idx_shift_substitutions_active
    ON shift_substitutions(starts_at, ends_at) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_status_requests_queue
    ON status_requests(queue_name, request_status, position_override, queued_at, id)
    WHERE queue_name IS NOT NULL AND request_status = 'pending';
CREATE INDEX IF NOT EXISTS idx_status_requests_non_queue
    ON status_requests(request_status, requested_at DESC)
    WHERE queue_name IS NULL;
CREATE INDEX IF NOT EXISTS idx_status_requests_overtime
    ON status_requests(expected_end_at) WHERE request_status = 'active' AND ended_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_employee_status_history_user_time
    ON employee_status_history(user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_status_request_events_request_time
    ON status_request_events(status_request_id, created_at);

INSERT INTO status_type_rules(
    status_code, display_name, queue_name, requires_approval,
    default_duration_minutes, minimum_duration_minutes, maximum_duration_minutes
) VALUES
    ('working', '工作', NULL, false, NULL, NULL, NULL),
    ('short_break', '小休', 'short_break', true, NULL, NULL, NULL),
    ('long_break', '大休', 'long_break', true, NULL, NULL, NULL),
    ('meal', '吃饭', NULL, true, NULL, NULL, NULL),
    ('coaching', '辅导', NULL, true, NULL, NULL, NULL),
    ('meeting', '会议', NULL, true, NULL, NULL, NULL)
ON CONFLICT (status_code) DO NOTHING;
