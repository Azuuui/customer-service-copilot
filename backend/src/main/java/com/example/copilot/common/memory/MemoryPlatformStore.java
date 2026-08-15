package com.example.copilot.common.memory;

import com.example.copilot.access.RoleCode;
import com.example.copilot.audit.AuditEntry;
import com.example.copilot.audit.AuditRepository;
import com.example.copilot.identity.EnterpriseIdentity;
import com.example.copilot.identity.IdentityRepository;
import com.example.copilot.identity.UserAccount;
import com.example.copilot.session.SessionRecord;
import com.example.copilot.session.SessionRepository;
import com.example.copilot.settings.AdminMenuItem;
import com.example.copilot.settings.MenuOrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "copilot.persistence", havingValue = "memory", matchIfMissing = true)
public class MemoryPlatformStore implements IdentityRepository, SessionRepository, AuditRepository, MenuOrderRepository {
    private final Map<String, UserAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final List<AuditEntry> auditEntries = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> menuOrders = new ConcurrentHashMap<>();

    private static final List<AdminMenuItem> DEFAULT_MENU = List.of(
            new AdminMenuItem("overview", "管理总览", "overview"),
            new AdminMenuItem("status_requests", "状态申请", "status-requests"),
            new AdminMenuItem("feedback", "待维护词与答案反馈", "feedback"),
            new AdminMenuItem("knowledge", "知识库管理", "knowledge"),
            new AdminMenuItem("announcements", "公告管理", "announcements"),
            new AdminMenuItem("schedules", "班务与值班", "schedules"),
            new AdminMenuItem("taxonomy", "类目与标签", "taxonomy"),
            new AdminMenuItem("accounts", "账号与权限", "accounts"),
            new AdminMenuItem("audit", "操作日志", "audit"),
            new AdminMenuItem("settings", "系统设置", "settings")
    );

    @Override
    public Optional<UserAccount> find(String employeeId) {
        return Optional.ofNullable(accounts.get(employeeId));
    }

    @Override
    public UserAccount create(EnterpriseIdentity identity, Set<RoleCode> initialRoles) {
        UserAccount created = new UserAccount(identity.employeeId(), identity.name(), UserAccount.Status.active, initialRoles);
        UserAccount existing = accounts.putIfAbsent(identity.employeeId(), created);
        return existing == null ? created : existing;
    }

    @Override
    public UserAccount refreshProfile(String employeeId, String name) {
        return accounts.compute(employeeId, (key, account) -> copy(account, name, account.status(), account.roles()));
    }

    @Override
    public UserAccount replaceRoles(String employeeId, Set<RoleCode> roles) {
        return accounts.compute(employeeId, (key, account) -> copy(account, account.name(), account.status(), roles));
    }

    @Override
    public UserAccount changeStatus(String employeeId, UserAccount.Status status) {
        return accounts.compute(employeeId, (key, account) -> copy(account, account.name(), status, account.roles()));
    }

    private UserAccount copy(UserAccount account, String name, UserAccount.Status status, Set<RoleCode> roles) {
        if (account == null) throw new IllegalArgumentException("account_not_found");
        return new UserAccount(account.employeeId(), name, status, roles);
    }

    @Override
    public void save(SessionRecord session) { sessions.put(session.tokenHash(), session); }

    @Override
    public Optional<SessionRecord> findActive(String tokenHash, Instant now) {
        return Optional.ofNullable(sessions.get(tokenHash))
                .filter(session -> !session.invalidated() && session.expiresAt().isAfter(now));
    }

    @Override
    public void invalidateByEmployeeId(String employeeId) {
        sessions.replaceAll((key, session) -> session.employeeId().equals(employeeId)
                ? new SessionRecord(session.tokenHash(), session.employeeId(), session.loginMethod(), session.expiresAt(), true)
                : session);
    }

    @Override
    public void invalidate(String tokenHash) {
        sessions.computeIfPresent(tokenHash, (key, session) ->
                new SessionRecord(session.tokenHash(), session.employeeId(), session.loginMethod(), session.expiresAt(), true));
    }

    @Override
    public void append(AuditEntry entry) { auditEntries.add(entry); }

    @Override
    public List<AuditEntry> newestFirst(int limit) {
        return auditEntries.stream()
                .sorted(Comparator.comparing(AuditEntry::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<AdminMenuItem> defaultItems() { return DEFAULT_MENU; }

    @Override
    public List<String> findOrder(String employeeId) {
        return menuOrders.getOrDefault(employeeId, DEFAULT_MENU.stream().map(AdminMenuItem::moduleCode).toList());
    }

    @Override
    public void saveOrder(String employeeId, List<String> moduleCodes) {
        menuOrders.put(employeeId, List.copyOf(moduleCodes));
    }
}
