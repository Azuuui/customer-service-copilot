package com.example.copilot.access;

import java.util.Set;

public enum RoleCode {
    customer_service,
    supervisor,
    knowledge_admin,
    system_admin;

    public boolean isAdministrator() {
        return this != customer_service;
    }

    public Set<String> readableModules() {
        return switch (this) {
            case customer_service -> Set.of();
            case supervisor -> Set.of("overview", "status_requests", "schedules");
            case knowledge_admin -> Set.of("overview", "feedback", "knowledge", "taxonomy");
            case system_admin -> Set.of(
                    "overview", "status_requests", "feedback", "knowledge", "announcements",
                    "schedules", "taxonomy", "accounts", "audit", "settings"
            );
        };
    }
}
