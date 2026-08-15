package com.example.copilot.session;

import com.example.copilot.identity.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class SessionAuthenticationInterceptor implements HandlerInterceptor {
    public static final String EMPLOYEE_ID = SessionAuthenticationInterceptor.class.getName() + ".employeeId";
    private final SessionService sessions;
    private final IdentityService identities;

    public SessionAuthenticationInterceptor(SessionService sessions, IdentityService identities) {
        this.sessions = sessions;
        this.identities = identities;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = SessionTokenResolver.resolve(request);
        String employeeId = sessions.employeeId(token);
        identities.current(employeeId);
        request.setAttribute(EMPLOYEE_ID, employeeId);
        return true;
    }
}
