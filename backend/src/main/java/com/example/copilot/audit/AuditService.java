package com.example.copilot.audit;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final AuditRepository repository;
    private final Clock clock;

    @Autowired
    public AuditService(AuditRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(String actor, String module, String action, String targetType, String targetId) {
        record(actor, module, action, targetType, targetId, Map.of());
    }

    public void record(String actor, String module, String action, String targetType, String targetId, Map<String, Object> summary) {
        repository.append(new AuditEntry(Instant.now(clock), actor, module, action, targetType, targetId, AuditEntry.Result.success, summary));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String actor, String module, String action, String targetType, String targetId, Map<String, Object> summary) {
        repository.append(new AuditEntry(Instant.now(clock), actor, module, action, targetType, targetId, AuditEntry.Result.failure, summary));
    }

    public List<AuditEntry> newestFirst(int limit) { return repository.newestFirst(Math.min(Math.max(limit, 1), 100)); }
}
