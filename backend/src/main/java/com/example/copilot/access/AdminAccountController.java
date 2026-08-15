package com.example.copilot.access;

import com.example.copilot.identity.IdentityService;
import com.example.copilot.identity.UserAccount;
import com.example.copilot.common.ApiException;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {
    private final AccessService access;
    private final IdentityService identities;

    public AdminAccountController(AccessService access, IdentityService identities) {
        this.access = access;
        this.identities = identities;
    }

    @PutMapping("/{employeeId}/roles")
    public UserAccount replaceRoles(HttpServletRequest request, @PathVariable String employeeId, @Valid @RequestBody RolesRequest body) {
        String actor = actor(request);
        access.requireSystemAdmin(actor);
        Set<RoleCode> roles;
        try {
            roles = body.roles().stream().map(RoleCode::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_role");
        }
        return identities.replaceRoles(actor, employeeId, roles);
    }

    @PostMapping("/{employeeId}/disable")
    public UserAccount disable(HttpServletRequest request, @PathVariable String employeeId) {
        String actor = actor(request);
        access.requireSystemAdmin(actor);
        return identities.changeStatus(actor, employeeId, UserAccount.Status.disabled);
    }

    @PostMapping("/{employeeId}/enable")
    public UserAccount enable(HttpServletRequest request, @PathVariable String employeeId) {
        String actor = actor(request);
        access.requireSystemAdmin(actor);
        return identities.changeStatus(actor, employeeId, UserAccount.Status.active);
    }

    private String actor(HttpServletRequest request) { return (String) request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID); }
    public record RolesRequest(@NotEmpty Set<String> roles) {}
}
