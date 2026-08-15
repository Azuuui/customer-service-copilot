package com.example.copilot.settings;

import java.util.List;

public interface MenuOrderRepository {
    List<AdminMenuItem> defaultItems();
    List<String> findOrder(String employeeId);
    void saveOrder(String employeeId, List<String> moduleCodes);
}
