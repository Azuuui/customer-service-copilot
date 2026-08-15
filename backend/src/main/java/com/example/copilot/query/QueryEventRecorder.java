package com.example.copilot.query;

import java.util.List;
import java.util.Map;

public interface QueryEventRecorder {
    void record(String sessionKey, String query, String requestKind, int resultCount, long latencyMs, String requestId);
    Map<String, Object> metrics();

    record TopQuery(String query, long count) {}
}
