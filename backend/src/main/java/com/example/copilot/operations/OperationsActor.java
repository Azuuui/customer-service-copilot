package com.example.copilot.operations;

import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class OperationsActor {
    private final JdbcClient jdbc;
    public OperationsActor(JdbcClient jdbc) { this.jdbc = jdbc; }
    public String employee(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));
    }
    public long userId(String employee) {
        return jdbc.sql("SELECT id FROM users WHERE dingtalk_user_id=:employee")
                .param("employee", employee).query(Long.class).single();
    }
}
