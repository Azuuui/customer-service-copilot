package com.example.copilot.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

public interface FeedbackService {
    Result report(String sessionKey, String query, String type, String detail, boolean confirmDuplicate);
    record Result(String status, boolean confirmationRequired) {}

    @Service
    @ConditionalOnProperty(name = "copilot.persistence", havingValue = "memory", matchIfMissing = true)
    class Memory implements FeedbackService {
        @Override public Result report(String sessionKey, String query, String type, String detail, boolean confirmDuplicate) {
            return new Result("accepted", false);
        }
    }

    @Service
    @ConditionalOnProperty(name = "copilot.persistence", havingValue = "jdbc")
    class Jdbc implements FeedbackService {
        private final JdbcClient jdbc;
        Jdbc(JdbcClient jdbc) { this.jdbc = jdbc; }

        @Override
        @Transactional
        public Result report(String sessionKey, String query, String type, String detail, boolean confirmDuplicate) {
            String hash = sha256(sessionKey);
            jdbc.sql("""
                    INSERT INTO anonymous_sessions(session_key_hash, expires_at) VALUES (:hash, now() + interval '30 days')
                    ON CONFLICT (session_key_hash) DO UPDATE SET last_seen_at=now(), expires_at=now()+interval '30 days'
                    """).param("hash", hash).update();
            boolean duplicate = jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM feedback_reports r
                    JOIN anonymous_sessions s ON s.id=r.anonymous_session_id
                    WHERE s.session_key_hash=:hash AND lower(trim(r.query_text))=:query
                      AND r.created_at >= now()-interval '24 hours')
                    """).param("hash", hash).param("query", query.trim().toLowerCase()).query(Boolean.class).single();
            if (duplicate && !confirmDuplicate) return new Result("confirmation_required", true);
            Long caseId = jdbc.sql("""
                    INSERT INTO feedback_cases(normalized_query, latest_query_text, report_count)
                    VALUES (:normalized, :query, 1)
                    ON CONFLICT (normalized_query) WHERE status IN ('pending','processing')
                    DO UPDATE SET latest_query_text=excluded.latest_query_text,
                      report_count=feedback_cases.report_count+1, updated_at=now()
                    RETURNING id
                    """).param("normalized", query.trim().toLowerCase()).param("query", query).query(Long.class).single();
            jdbc.sql("""
                    INSERT INTO feedback_reports(feedback_case_id, anonymous_session_id, query_text,
                      feedback_type, detail, duplicate_confirmation_required, duplicate_confirmed_at)
                    SELECT :caseId, id, :query, :type, :detail, :duplicate,
                      CASE WHEN :duplicate THEN now() ELSE NULL END
                    FROM anonymous_sessions WHERE session_key_hash=:hash
                    """).param("caseId", caseId).param("query", query).param("type", type)
                    .param("detail", detail).param("duplicate", duplicate).param("hash", hash).update();
            return new Result("accepted", false);
        }

        private String sha256(String value) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
    }
}
