package com.example.copilot.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEntry(
        Instant createdAt,
        String actorEmployeeId,
        String module,
        String action,
        String targetType,
        String targetId,
        Result result,
        Map<String, Object> summary
) {
    public AuditEntry {
        summary = Map.copyOf(summary);
    }

    public enum Result { success, failure }
}
