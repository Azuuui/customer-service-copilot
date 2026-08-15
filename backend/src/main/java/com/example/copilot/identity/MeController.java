package com.example.copilot.identity;

import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final IdentityService identities;

    public MeController(IdentityService identities) { this.identities = identities; }

    @GetMapping
    public MeResponse current(HttpServletRequest request) {
        UserAccount user = identities.current((String) request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));
        return new MeResponse(user, user.roles().stream().map(Enum::name).sorted().toList());
    }

    public record MeResponse(UserAccount user, List<String> roles) {}
}
