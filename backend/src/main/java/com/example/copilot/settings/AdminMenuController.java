package com.example.copilot.settings;

import com.example.copilot.access.AccessService;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/menu-order")
public class AdminMenuController {
    private final AccessService access;
    private final MenuService menus;

    public AdminMenuController(AccessService access, MenuService menus) {
        this.access = access;
        this.menus = menus;
    }

    @GetMapping
    public MenuService.MenuOrder current(HttpServletRequest request) {
        String employeeId = actor(request);
        return menus.current(employeeId, access.readableModules(employeeId));
    }

    @PutMapping
    public MenuService.MenuOrder save(HttpServletRequest request, @Valid @RequestBody MenuOrderRequest body) {
        String employeeId = actor(request);
        return menus.save(employeeId, body.moduleCodes(), access.readableModules(employeeId));
    }

    private String actor(HttpServletRequest request) { return (String) request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID); }
    public record MenuOrderRequest(@NotEmpty List<String> moduleCodes) {}
}
