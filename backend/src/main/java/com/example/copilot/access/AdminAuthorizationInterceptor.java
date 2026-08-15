package com.example.copilot.access;

import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 服务端后台模块授权；前端菜单从不作为安全边界。 */
public class AdminAuthorizationInterceptor implements HandlerInterceptor {
    private final AccessService access;
    public AdminAuthorizationInterceptor(AccessService access) { this.access = access; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String employee = String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/accounts")) access.requireSystemAdmin(employee);
        else if (path.startsWith("/api/v1/admin/knowledge-transfer") || path.startsWith("/api/v1/admin/knowledge")) access.requireModule(employee,"knowledge");
        else if (path.startsWith("/api/v1/admin/taxonomy")) access.requireModule(employee,"taxonomy");
        else if (path.startsWith("/api/v1/admin/feedback")) access.requireModule(employee,"feedback");
        else if (path.startsWith("/api/v1/admin/announcements")) access.requireModule(employee,"announcements");
        else if (path.startsWith("/api/v1/admin/schedules")) access.requireModule(employee,"schedules");
        else if (path.startsWith("/api/v1/admin/status")) access.requireModule(employee,"status_requests");
        else if (path.startsWith("/api/v1/admin/query-metrics")) access.requireModule(employee,"overview");
        else if (path.startsWith("/api/v1/admin/audit")) access.requireModule(employee,"audit");
        else if (path.startsWith("/api/v1/admin/settings")) access.requireModule(employee,"settings");
        else access.requireAdministrator(employee);
        return true;
    }
}
