package com.example.copilot.settings;

import com.example.copilot.audit.AuditService;
import com.example.copilot.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MenuService {
    private final MenuOrderRepository repository;
    private final AuditService audit;

    public MenuService(MenuOrderRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public MenuOrder current(String employeeId, Set<String> readableModules) {
        List<AdminMenuItem> items = repository.defaultItems();
        List<String> order = repository.findOrder(employeeId);
        var byCode = items.stream().collect(java.util.stream.Collectors.toMap(AdminMenuItem::moduleCode, item -> item));
        var orderedCodes = new java.util.ArrayList<String>();
        order.stream().filter(readableModules::contains).forEach(orderedCodes::add);
        items.stream().map(AdminMenuItem::moduleCode)
                .filter(readableModules::contains)
                .filter(code -> !orderedCodes.contains(code))
                .forEach(orderedCodes::add);
        return new MenuOrder(orderedCodes.stream().map(byCode::get).toList());
    }

    public MenuOrder save(String employeeId, List<String> moduleCodes, Set<String> readableModules) {
        List<String> expected = repository.defaultItems().stream().map(AdminMenuItem::moduleCode)
                .filter(readableModules::contains).toList();
        if (moduleCodes.size() != expected.size() || !new HashSet<>(moduleCodes).equals(new HashSet<>(expected))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "menu_order_must_contain_all_items");
        }
        repository.saveOrder(employeeId, moduleCodes);
        audit.record(employeeId, "settings", "MENU_ORDER_UPDATED", "admin_menu", employeeId);
        return current(employeeId, readableModules);
    }

    public record MenuOrder(List<AdminMenuItem> items) {}
}
