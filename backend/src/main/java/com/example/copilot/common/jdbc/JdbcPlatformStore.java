package com.example.copilot.common.jdbc;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "copilot.persistence", havingValue = "jdbc")
public class JdbcPlatformStore implements IdentityRepository, SessionRepository, AuditRepository, MenuOrderRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcPlatformStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<UserAccount> find(String employeeId) {
        return jdbc.sql("""
                SELECT u.dingtalk_user_id, u.name, u.status, COALESCE(string_agg(r.code, ','), '') AS role_codes
                FROM users u LEFT JOIN user_roles ur ON ur.user_id = u.id
                LEFT JOIN roles r ON r.id = ur.role_id
                WHERE u.dingtalk_user_id = :employeeId
                GROUP BY u.dingtalk_user_id, u.name, u.status
                """).param("employeeId", employeeId).query(this::mapAccount).optional();
    }

    @Override
    @Transactional
    public UserAccount create(EnterpriseIdentity identity, Set<RoleCode> initialRoles) {
        jdbc.sql("""
                INSERT INTO users(corp_id, dingtalk_user_id, name, status)
                VALUES (:corpId, :employeeId, :name, 'active')
                ON CONFLICT (corp_id, dingtalk_user_id) DO NOTHING
                """).param("corpId", identity.corpId()).param("employeeId", identity.employeeId()).param("name", identity.name()).update();
        for (RoleCode role : initialRoles) {
            jdbc.sql("""
                    INSERT INTO user_roles(user_id, role_id)
                    SELECT u.id, r.id FROM users u CROSS JOIN roles r
                    WHERE u.dingtalk_user_id = :employeeId AND r.code = :role
                    ON CONFLICT DO NOTHING
                    """).param("employeeId", identity.employeeId()).param("role", role.name()).update();
        }
        jdbc.sql("""
                INSERT INTO employee_current_statuses(user_id, status_code)
                SELECT id, 'working' FROM users WHERE dingtalk_user_id = :employeeId
                ON CONFLICT (user_id) DO NOTHING
                """).param("employeeId", identity.employeeId()).update();
        return find(identity.employeeId()).orElseThrow();
    }

    @Override
    public UserAccount refreshProfile(String employeeId, String name) {
        jdbc.sql("UPDATE users SET name = :name, last_login_at = now(), updated_at = now() WHERE dingtalk_user_id = :employeeId")
                .param("name", name).param("employeeId", employeeId).update();
        return find(employeeId).orElseThrow();
    }

    @Override
    @Transactional
    public UserAccount replaceRoles(String employeeId, Set<RoleCode> roles) {
        jdbc.sql("DELETE FROM user_roles WHERE user_id = (SELECT id FROM users WHERE dingtalk_user_id = :employeeId)")
                .param("employeeId", employeeId).update();
        for (RoleCode role : roles) {
            jdbc.sql("""
                    INSERT INTO user_roles(user_id, role_id)
                    SELECT u.id, r.id FROM users u CROSS JOIN roles r
                    WHERE u.dingtalk_user_id = :employeeId AND r.code = :role
                    """).param("employeeId", employeeId).param("role", role.name()).update();
        }
        return find(employeeId).orElseThrow();
    }

    @Override
    public UserAccount changeStatus(String employeeId, UserAccount.Status status) {
        jdbc.sql("UPDATE users SET status = :status, disabled_at = CASE WHEN :status = 'disabled' THEN now() ELSE NULL END, updated_at = now() WHERE dingtalk_user_id = :employeeId")
                .param("status", status.name()).param("employeeId", employeeId).update();
        return find(employeeId).orElseThrow();
    }

    @Override
    public void save(SessionRecord session) {
        jdbc.sql("""
                INSERT INTO user_sessions(user_id, session_token_hash, login_method, expires_at)
                SELECT id, :tokenHash, :loginMethod, :expiresAt FROM users WHERE dingtalk_user_id = :employeeId
                """).param("tokenHash", session.tokenHash()).param("loginMethod", session.loginMethod().name()).param("expiresAt", Timestamp.from(session.expiresAt()))
                .param("employeeId", session.employeeId()).update();
    }

    @Override
    public Optional<SessionRecord> findActive(String tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT s.session_token_hash, u.dingtalk_user_id, s.login_method, s.expires_at
                FROM user_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.session_token_hash = :tokenHash AND s.invalidated_at IS NULL AND s.expires_at > :now
                """).param("tokenHash", tokenHash).param("now", Timestamp.from(now))
                .query((rs, rowNum) -> new SessionRecord(rs.getString(1), rs.getString(2),
                        com.example.copilot.session.LoginMethod.valueOf(rs.getString(3)),
                        rs.getTimestamp(4).toInstant(), false)).optional();
    }

    @Override
    public void invalidateByEmployeeId(String employeeId) {
        jdbc.sql("UPDATE user_sessions SET invalidated_at = now(), invalidated_reason = 'account_disabled' WHERE user_id = (SELECT id FROM users WHERE dingtalk_user_id = :employeeId) AND invalidated_at IS NULL")
                .param("employeeId", employeeId).update();
    }

    @Override
    public void invalidate(String tokenHash) {
        jdbc.sql("UPDATE user_sessions SET invalidated_at = now(), invalidated_reason = 'logout' WHERE session_token_hash = :tokenHash AND invalidated_at IS NULL")
                .param("tokenHash", tokenHash).update();
    }

    @Override
    public void append(AuditEntry entry) {
        jdbc.sql("""
                INSERT INTO audit_logs(actor_user_id, module_code, action, target_type, target_id, result, change_summary, entry_point)
                VALUES ((SELECT id FROM users WHERE dingtalk_user_id = :actor), :module, :action, :targetType, :targetId, :result, CAST(:summary AS jsonb), 'api')
                """).param("actor", entry.actorEmployeeId()).param("module", entry.module()).param("action", entry.action())
                .param("targetType", entry.targetType()).param("targetId", entry.targetId()).param("result", entry.result().name())
                .param("summary", json(entry.summary())).update();
    }

    @Override
    public List<AuditEntry> newestFirst(int limit) {
        return jdbc.sql("""
                SELECT created_at, (SELECT dingtalk_user_id FROM users WHERE id = actor_user_id), module_code, action, target_type, target_id, result, change_summary
                FROM audit_logs ORDER BY created_at DESC LIMIT :limit
                """).param("limit", limit).query((rs, rowNum) -> new AuditEntry(
                rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                AuditEntry.Result.valueOf(rs.getString(7)), jsonMap(rs.getString(8)))).list();
    }

    @Override
    public List<AdminMenuItem> defaultItems() {
        return jdbc.sql("SELECT module_code, display_name, route_anchor FROM admin_menu_items ORDER BY default_position")
                .query((rs, rowNum) -> new AdminMenuItem(rs.getString(1), rs.getString(2), rs.getString(3))).list();
    }

    @Override
    public List<String> findOrder(String employeeId) {
        List<String> saved = jdbc.sql("""
                SELECT m.module_code FROM user_menu_orders o JOIN admin_menu_items m ON m.id = o.menu_item_id
                JOIN users u ON u.id = o.user_id WHERE u.dingtalk_user_id = :employeeId ORDER BY o.position
                """).param("employeeId", employeeId).query((rs, rowNum) -> rs.getString(1)).list();
        return saved.isEmpty() ? defaultItems().stream().map(AdminMenuItem::moduleCode).toList() : saved;
    }

    @Override
    @Transactional
    public void saveOrder(String employeeId, List<String> moduleCodes) {
        jdbc.sql("DELETE FROM user_menu_orders WHERE user_id = (SELECT id FROM users WHERE dingtalk_user_id = :employeeId)")
                .param("employeeId", employeeId).update();
        for (int i = 0; i < moduleCodes.size(); i++) {
            jdbc.sql("""
                    INSERT INTO user_menu_orders(user_id, menu_item_id, position)
                    SELECT u.id, m.id, :position FROM users u CROSS JOIN admin_menu_items m
                    WHERE u.dingtalk_user_id = :employeeId AND m.module_code = :moduleCode
                    """).param("employeeId", employeeId).param("moduleCode", moduleCodes.get(i)).param("position", i + 1).update();
        }
    }

    private UserAccount mapAccount(ResultSet rs, int rowNum) throws SQLException {
        String roles = rs.getString("role_codes");
        Set<RoleCode> roleSet = roles == null || roles.isBlank() ? Set.of() : Arrays.stream(roles.split(",")).map(RoleCode::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new UserAccount(rs.getString("dingtalk_user_id"), rs.getString("name"), UserAccount.Status.valueOf(rs.getString("status")), roleSet);
    }

    private String json(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("audit_summary_invalid", exception); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String value) {
        try { return objectMapper.readValue(value, Map.class); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("audit_summary_invalid", exception); }
    }
}
