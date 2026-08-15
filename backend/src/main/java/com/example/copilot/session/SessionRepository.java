package com.example.copilot.session;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {
    void save(SessionRecord session);
    Optional<SessionRecord> findActive(String tokenHash, Instant now);
    void invalidateByEmployeeId(String employeeId);
    void invalidate(String tokenHash);
}
