package com.example.copilot.audit;

import com.example.copilot.access.AccessService;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {
    private final AccessService access;
    private final AuditService audit;

    public AdminAuditController(AccessService access, AuditService audit) {
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public AuditList list(HttpServletRequest request, @RequestParam(defaultValue = "50") int limit) {
        String employeeId = (String) request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID);
        access.requireModule(employeeId, "audit");
        return new AuditList(audit.newestFirst(limit));
    }

    public record AuditList(java.util.List<AuditEntry> items) {}
}
