package com.example.copilot.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import com.example.copilot.session.SessionService;
import com.example.copilot.session.LoginMethod;
import com.example.copilot.session.SessionTokenResolver;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identities;
    private final SessionService sessions;
    private final EnterpriseIdentityProvider provider;
    private final String mode;

    public AuthController(IdentityService identities, SessionService sessions,
                          org.springframework.beans.factory.ObjectProvider<EnterpriseIdentityProvider> provider,
                          @Value("${copilot.auth.mode:disabled}") String mode) {
        this.identities = identities;
        this.sessions = sessions;
        this.provider = provider.getIfAvailable();
        this.mode = mode;
    }

    @PostMapping("/mock-login")
    public LoginResponse mockLogin(@Valid @RequestBody MockLoginRequest request) {
        if (!"mock".equalsIgnoreCase(mode)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "mock_login_disabled");
        return response(identities.login(
                new EnterpriseIdentity("mock-corp", request.employeeId(), request.name()),
                LoginMethod.mock
        ));
    }

    @PostMapping("/dingtalk")
    public LoginResponse dingTalkLogin(@Valid @RequestBody DingTalkLoginRequest request) {
        return dingTalkLogin("sso", request);
    }

    @PostMapping("/dingtalk/{flow}")
    public LoginResponse dingTalkLogin(@PathVariable String flow, @Valid @RequestBody DingTalkLoginRequest request) {
        if (!"dingtalk".equalsIgnoreCase(mode) || provider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dingtalk_login_disabled");
        }
        LoginMethod loginMethod = switch (flow) {
            case "sso" -> LoginMethod.dingtalk_sso;
            case "qr" -> LoginMethod.dingtalk_qr;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dingtalk_flow_not_found");
        };
        return response(identities.login(provider.exchange(request.code()), loginMethod));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        sessions.invalidate(SessionTokenResolver.resolve(request));
    }

    private LoginResponse response(IdentityService.LoginResult result) {
        return new LoginResponse(result.sessionToken(), result.user());
    }

    public record MockLoginRequest(@NotBlank String employeeId, @NotBlank String name) {}
    public record DingTalkLoginRequest(@NotBlank String code) {}
    public record LoginResponse(String sessionToken, UserAccount user) {}
}
