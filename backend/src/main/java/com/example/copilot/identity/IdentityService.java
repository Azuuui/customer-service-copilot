package com.example.copilot.identity;

import com.example.copilot.access.RoleCode;
import com.example.copilot.audit.AuditService;
import com.example.copilot.common.ApiException;
import com.example.copilot.session.SessionService;
import com.example.copilot.session.LoginMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class IdentityService {
    private final IdentityRepository repository;
    private final SessionService sessions;
    private final AuditService audit;
    private final String bootstrapAdminId;

    public IdentityService(
            IdentityRepository repository,
            SessionService sessions,
            AuditService audit,
            @Value("${copilot.auth.bootstrap-admin-id:}") String bootstrapAdminId
    ) {
        this.repository = repository;
        this.sessions = sessions;
        this.audit = audit;
        this.bootstrapAdminId = bootstrapAdminId;
    }

    @Transactional
    public LoginResult login(EnterpriseIdentity identity, LoginMethod loginMethod) {
        UserAccount account = repository.find(identity.employeeId()).map(existing -> {
            if (!existing.active()) {
                audit.recordFailure(identity.employeeId(), "identity", "LOGIN_DENIED", "user", identity.employeeId(),
                        java.util.Map.of("reason", "account_disabled"));
                throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled");
            }
            return repository.refreshProfile(identity.employeeId(), identity.name());
        }).orElseGet(() -> {
            Set<RoleCode> roles = identity.employeeId().equals(bootstrapAdminId)
                    ? Set.of(RoleCode.system_admin)
                    : Set.of(RoleCode.customer_service);
            UserAccount created = repository.create(identity, roles);
            audit.record(identity.employeeId(), "identity", "ACCOUNT_CREATED", "user", identity.employeeId());
            return created;
        });
        String token = sessions.create(account.employeeId(), loginMethod);
        audit.record(account.employeeId(), "identity", "LOGIN", "session", account.employeeId());
        return new LoginResult(token, account);
    }

    public UserAccount current(String employeeId) {
        UserAccount account = repository.find(employeeId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "account_not_found"));
        if (!account.active()) throw new ApiException(HttpStatus.UNAUTHORIZED, "account_disabled");
        return account;
    }

    @Transactional
    public UserAccount replaceRoles(String actor, String employeeId, Set<RoleCode> roles) {
        requireManagedAccount(employeeId);
        UserAccount updated = repository.replaceRoles(employeeId, roles);
        audit.record(actor, "access", "ROLE_CHANGED", "user", employeeId);
        return updated;
    }

    @Transactional
    public UserAccount changeStatus(String actor, String employeeId, UserAccount.Status status) {
        requireManagedAccount(employeeId);
        UserAccount updated = repository.changeStatus(employeeId, status);
        if (status == UserAccount.Status.disabled) sessions.invalidateEmployee(employeeId);
        audit.record(actor, "identity", status == UserAccount.Status.disabled ? "ACCOUNT_DISABLED" : "ACCOUNT_ENABLED", "user", employeeId);
        return updated;
    }

    private UserAccount requireManagedAccount(String employeeId) {
        return repository.find(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account_not_found"));
    }

    public record LoginResult(String sessionToken, UserAccount user) {}
}
