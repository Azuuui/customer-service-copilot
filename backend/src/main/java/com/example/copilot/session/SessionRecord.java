package com.example.copilot.session;

import java.time.Instant;

public record SessionRecord(
        String tokenHash,
        String employeeId,
        LoginMethod loginMethod,
        Instant expiresAt,
        boolean invalidated
) {}
