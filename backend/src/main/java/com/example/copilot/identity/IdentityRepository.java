package com.example.copilot.identity;

import com.example.copilot.access.RoleCode;

import java.util.Optional;
import java.util.Set;

public interface IdentityRepository {
    Optional<UserAccount> find(String employeeId);
    UserAccount create(EnterpriseIdentity identity, Set<RoleCode> initialRoles);
    UserAccount refreshProfile(String employeeId, String name);
    UserAccount replaceRoles(String employeeId, Set<RoleCode> roles);
    UserAccount changeStatus(String employeeId, UserAccount.Status status);
}
