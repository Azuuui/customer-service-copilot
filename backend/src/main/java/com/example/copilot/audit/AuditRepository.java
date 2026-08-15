package com.example.copilot.audit;

import java.util.List;

public interface AuditRepository {
    void append(AuditEntry entry);
    List<AuditEntry> newestFirst(int limit);
}
