-- 查询事件、匿名会话、10 秒重复信号和反馈原始记录/汇总。

CREATE TABLE IF NOT EXISTS anonymous_sessions (
    id bigserial PRIMARY KEY,
    session_key_hash text NOT NULL UNIQUE,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    created_ip_hash text,
    CHECK (expires_at > first_seen_at)
);

CREATE TABLE IF NOT EXISTS query_events (
    id bigserial PRIMARY KEY,
    user_id bigint REFERENCES users(id),
    anonymous_session_id bigint REFERENCES anonymous_sessions(id),
    query_text text NOT NULL,
    normalized_query text NOT NULL,
    request_kind text NOT NULL DEFAULT 'query'
        CHECK (request_kind IN ('query', 'display_more', 'refresh', 'automatic_retry')),
    is_counted_query boolean NOT NULL DEFAULT true,
    result_count integer CHECK (result_count IS NULL OR result_count >= 0),
    response_status text NOT NULL CHECK (response_status IN ('success', 'no_match', 'failure')),
    latency_ms integer CHECK (latency_ms IS NULL OR latency_ms >= 0),
    duplicate_of_query_event_id bigint REFERENCES query_events(id),
    is_repeat_within_10s boolean NOT NULL DEFAULT false,
    repeat_window_seconds smallint NOT NULL DEFAULT 10 CHECK (repeat_window_seconds = 10),
    request_id text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(user_id, anonymous_session_id) = 1),
    CHECK (
        (request_kind = 'query' AND is_counted_query = true)
        OR (request_kind <> 'query' AND is_counted_query = false)
    ),
    CHECK (
        (is_repeat_within_10s = true AND duplicate_of_query_event_id IS NOT NULL)
        OR (is_repeat_within_10s = false AND duplicate_of_query_event_id IS NULL)
    )
);

CREATE OR REPLACE FUNCTION validate_query_repeat_window()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    previous_event query_events%ROWTYPE;
BEGIN
    IF NEW.is_repeat_within_10s = false THEN
        RETURN NEW;
    END IF;
    SELECT * INTO previous_event
    FROM query_events
    WHERE id = NEW.duplicate_of_query_event_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION '重复查询引用的原事件不存在';
    END IF;
    IF previous_event.request_kind <> 'query'
        OR previous_event.normalized_query IS DISTINCT FROM NEW.normalized_query
        OR previous_event.user_id IS DISTINCT FROM NEW.user_id
        OR previous_event.anonymous_session_id IS DISTINCT FROM NEW.anonymous_session_id
        OR NEW.created_at < previous_event.created_at
        OR NEW.created_at > previous_event.created_at + interval '10 seconds'
    THEN
        RAISE EXCEPTION '重复查询必须是同一身份、同一查询词且间隔不超过 10 秒';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_query_repeat_window
BEFORE INSERT OR UPDATE OF duplicate_of_query_event_id, is_repeat_within_10s, created_at
ON query_events
FOR EACH ROW EXECUTE FUNCTION validate_query_repeat_window();

CREATE TABLE IF NOT EXISTS feedback_cases (
    id bigserial PRIMARY KEY,
    normalized_query text NOT NULL,
    latest_query_text text NOT NULL,
    source text NOT NULL DEFAULT 'query_page' CHECK (source IN ('query_page', 'admin_review')),
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processing', 'updated', 'closed')),
    priority text NOT NULL DEFAULT 'normal' CHECK (priority IN ('urgent', 'normal', 'low')),
    report_count integer NOT NULL DEFAULT 0 CHECK (report_count >= 0),
    assigned_to bigint REFERENCES users(id),
    linked_knowledge_entry_id bigint REFERENCES knowledge_entries(id),
    ignored_at timestamptz,
    ignored_by bigint REFERENCES users(id),
    ignore_reason text,
    ignore_undone_at timestamptz,
    ignore_undone_by bigint REFERENCES users(id),
    resolved_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS feedback_reports (
    id bigserial PRIMARY KEY,
    feedback_case_id bigint NOT NULL REFERENCES feedback_cases(id),
    query_event_id bigint REFERENCES query_events(id),
    user_id bigint REFERENCES users(id),
    anonymous_session_id bigint REFERENCES anonymous_sessions(id),
    query_text text NOT NULL,
    feedback_type text NOT NULL DEFAULT 'answer_issue'
        CHECK (feedback_type IN ('answer_issue', 'no_match', 'outdated', 'incomplete', 'unclear')),
    detail text,
    duplicate_confirmation_required boolean NOT NULL DEFAULT false,
    duplicate_confirmed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(user_id, anonymous_session_id) = 1),
    CHECK (
        duplicate_confirmation_required = false
        OR duplicate_confirmed_at IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_anonymous_sessions_expiry
    ON anonymous_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_query_events_daily_count
    ON query_events(created_at, is_counted_query, response_status);
CREATE INDEX IF NOT EXISTS idx_query_events_repeat_lookup_user
    ON query_events(user_id, normalized_query, created_at DESC)
    WHERE user_id IS NOT NULL AND request_kind = 'query';
CREATE INDEX IF NOT EXISTS idx_query_events_repeat_lookup_anonymous
    ON query_events(anonymous_session_id, normalized_query, created_at DESC)
    WHERE anonymous_session_id IS NOT NULL AND request_kind = 'query';
CREATE INDEX IF NOT EXISTS idx_query_events_repeat_dashboard
    ON query_events(created_at, normalized_query)
    WHERE is_repeat_within_10s = true;
CREATE INDEX IF NOT EXISTS idx_feedback_cases_worklist
    ON feedback_cases(status, priority, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_feedback_cases_unique_open_query
    ON feedback_cases(normalized_query) WHERE status IN ('pending', 'processing');
CREATE INDEX IF NOT EXISTS idx_feedback_reports_case_time
    ON feedback_reports(feedback_case_id, created_at DESC);
