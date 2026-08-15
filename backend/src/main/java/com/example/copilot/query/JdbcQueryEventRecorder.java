package com.example.copilot.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "copilot.persistence", havingValue = "jdbc")
public class JdbcQueryEventRecorder implements QueryEventRecorder {
    private final JdbcClient jdbc;
    public JdbcQueryEventRecorder(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void record(String sessionKey, String query, String requestKind, int resultCount, long latencyMs, String requestId) {
        String sessionHash = sha256(sessionKey);
        jdbc.sql("""
                INSERT INTO anonymous_sessions(session_key_hash, expires_at)
                VALUES (:hash, now() + interval '30 days')
                ON CONFLICT (session_key_hash) DO UPDATE SET last_seen_at = now(), expires_at = now() + interval '30 days'
                """).param("hash", sessionHash).update();
        boolean counted = "query".equals(requestKind);
        String normalized = query.trim().toLowerCase();
        Long duplicateId = counted ? jdbc.sql("""
                SELECT q.id FROM query_events q JOIN anonymous_sessions s ON s.id = q.anonymous_session_id
                WHERE s.session_key_hash = :hash AND q.normalized_query = :normalized
                  AND q.request_kind = 'query' AND q.created_at >= now() - interval '10 seconds'
                ORDER BY q.created_at DESC LIMIT 1
                """).param("hash", sessionHash).param("normalized", normalized).query(Long.class).optional().orElse(null) : null;
        jdbc.sql("""
                INSERT INTO query_events(anonymous_session_id, query_text, normalized_query, request_kind,
                    is_counted_query, result_count, response_status, latency_ms, duplicate_of_query_event_id,
                    is_repeat_within_10s, request_id)
                SELECT id, :query, :normalized, :kind, :counted, :resultCount, :status, :latency,
                    :duplicateId, :isRepeat, :requestId FROM anonymous_sessions WHERE session_key_hash = :hash
                """).param("query", query).param("normalized", normalized).param("kind", requestKind)
                .param("counted", counted).param("resultCount", resultCount)
                .param("status", resultCount > 0 ? "success" : "no_match").param("latency", (int) latencyMs)
                .param("duplicateId", duplicateId).param("isRepeat", duplicateId != null)
                .param("requestId", requestId).param("hash", sessionHash).update();
    }

    @Override
    public Map<String, Object> metrics() {
        Map<String, Object> counts = jdbc.sql("""
                SELECT count(*) FILTER (WHERE is_counted_query) AS today_queries,
                       count(*) FILTER (WHERE is_repeat_within_10s) AS repeat_queries
                FROM query_events WHERE created_at >= date_trunc('day', now())
                """).query((rs, rowNum) -> Map.<String, Object>of(
                        "todayQueries", rs.getLong(1), "repeatQueries", rs.getLong(2))).single();
        List<TopQuery> top = jdbc.sql("""
                SELECT normalized_query, count(*) FROM query_events
                WHERE created_at >= date_trunc('day', now()) AND is_counted_query
                GROUP BY normalized_query ORDER BY count(*) DESC, normalized_query LIMIT 10
                """).query((rs, rowNum) -> new TopQuery(rs.getString(1), rs.getLong(2))).list();
        return Map.of("todayQueries", counts.get("todayQueries"), "repeatQueries", counts.get("repeatQueries"), "topQueries", top);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
