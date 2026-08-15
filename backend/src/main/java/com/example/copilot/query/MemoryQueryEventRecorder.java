package com.example.copilot.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@ConditionalOnProperty(name = "copilot.persistence", havingValue = "memory", matchIfMissing = true)
public class MemoryQueryEventRecorder implements QueryEventRecorder {
    @Override public void record(String sessionKey, String query, String requestKind, int resultCount, long latencyMs, String requestId) {}
    @Override public Map<String, Object> metrics() { return Map.of("todayQueries", 0, "repeatQueries", 0, "topQueries", java.util.List.of()); }
}
