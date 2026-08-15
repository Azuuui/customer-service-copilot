package com.example.copilot.session;

import com.example.copilot.identity.IdentityService;
import com.example.copilot.access.AccessService;
import com.example.copilot.access.AdminAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnBean({SessionService.class, IdentityService.class})
public class WebConfig implements WebMvcConfigurer {
    private final SessionService sessions;
    private final IdentityService identities;
    private final AccessService access;

    public WebConfig(SessionService sessions, IdentityService identities, AccessService access) {
        this.sessions = sessions;
        this.identities = identities;
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthenticationInterceptor(sessions, identities))
                .addPathPatterns("/api/v1/me", "/api/v1/admin/**", "/api/v1/status/**");
        registry.addInterceptor(new AdminAuthorizationInterceptor(access))
                .addPathPatterns("/api/v1/admin/**");
    }
}
