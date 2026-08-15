package com.example.copilot.access;

import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@ConditionalOnProperty(name="copilot.persistence",havingValue="jdbc")
public class JdbcAccountReadController {
    private final JdbcClient jdbc;private final AccessService access;
    public JdbcAccountReadController(JdbcClient jdbc,AccessService access){this.jdbc=jdbc;this.access=access;}
    @GetMapping public List<Account> list(HttpServletRequest request,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
        access.requireSystemAdmin(String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID)));
        return jdbc.sql("SELECT u.dingtalk_user_id employee_id,u.name,u.status,string_agg(r.code,',' ORDER BY r.code) roles FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id GROUP BY u.id ORDER BY u.created_at DESC LIMIT :size OFFSET :offset").param("size",Math.min(Math.max(size,1),100)).param("offset",Math.max(page,0)*size).query(Account.class).list();
    }
    public record Account(String employeeId,String name,String status,String roles){}
}
