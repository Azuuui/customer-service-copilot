package com.example.copilot.access;

import com.example.copilot.common.ApiException;
import com.example.copilot.identity.IdentityService;
import com.example.copilot.identity.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AccessService {
    private final IdentityService identityService;

    public AccessService(IdentityService identityService) { this.identityService = identityService; }

    public UserAccount requireAdministrator(String employeeId) {
        UserAccount account = identityService.current(employeeId);
        if (account.roles().stream().noneMatch(RoleCode::isAdministrator)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "permission_denied");
        }
        return account;
    }

    public UserAccount requireSystemAdmin(String employeeId) {
        UserAccount account = identityService.current(employeeId);
        if (!account.roles().contains(RoleCode.system_admin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "permission_denied");
        }
        return account;
    }

    public Set<String> readableModules(String employeeId) {
        return requireAdministrator(employeeId).roles().stream()
                .flatMap(role -> role.readableModules().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public UserAccount requireModule(String employeeId, String moduleCode) {
        UserAccount account = requireAdministrator(employeeId);
        if (account.roles().stream().noneMatch(role -> role.readableModules().contains(moduleCode))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "permission_denied");
        }
        return account;
    }
}
