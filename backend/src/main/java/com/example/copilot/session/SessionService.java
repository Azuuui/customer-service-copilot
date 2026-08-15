package com.example.copilot.session;

import com.example.copilot.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SessionService {
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private final SessionRepository repository;
    private final Clock clock;

    @Autowired
    public SessionService(SessionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SessionService(SessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public String create(String employeeId, LoginMethod loginMethod) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        repository.save(new SessionRecord(hash(token), employeeId, loginMethod, Instant.now(clock).plus(SESSION_TTL), false));
        return token;
    }

    public String employeeId(String token) {
        if (token == null || token.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "session_required");
        return repository.findActive(hash(token), Instant.now(clock))
                .map(SessionRecord::employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "session_invalid"));
    }

    public void invalidate(String token) {
        if (token != null && !token.isBlank()) repository.invalidate(hash(token));
    }

    public void invalidateEmployee(String employeeId) { repository.invalidateByEmployeeId(employeeId); }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha256_unavailable", exception);
        }
    }
}
