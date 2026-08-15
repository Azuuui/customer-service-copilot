package com.example.copilot.identity;

import com.example.copilot.access.RoleCode;

import java.util.Set;

public record UserAccount(String employeeId, String name, Status status, Set<RoleCode> roles) {
    public enum Status { active, disabled }

    public UserAccount {
        roles = Set.copyOf(roles);
    }

    public boolean active() { return status == Status.active; }
}
