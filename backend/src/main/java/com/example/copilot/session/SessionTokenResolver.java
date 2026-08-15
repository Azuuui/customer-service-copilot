package com.example.copilot.session;

import jakarta.servlet.http.HttpServletRequest;

public final class SessionTokenResolver {
    private static final String BEARER_PREFIX = "Bearer ";

    private SessionTokenResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String token = request.getHeader("X-Session-Token");
        if (token != null && !token.isBlank()) return token;

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String bearerToken = authorization.substring(BEARER_PREFIX.length()).trim();
            if (!bearerToken.isBlank()) return bearerToken;
        }

        var cookies = request.getCookies();
        if (cookies != null) {
            for (var cookie : cookies) {
                if ("copilot_session".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
