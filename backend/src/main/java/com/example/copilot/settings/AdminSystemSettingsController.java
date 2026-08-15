package com.example.copilot.settings;

import com.example.copilot.access.AccessService;
import com.example.copilot.audit.AuditService;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/settings")
@ConditionalOnProperty(name="copilot.persistence",havingValue="jdbc")
public class AdminSystemSettingsController {
    private final JdbcClient jdbc;private final AccessService access;private final AuditService audit;
    public AdminSystemSettingsController(JdbcClient jdbc,AccessService access,AuditService audit){this.jdbc=jdbc;this.access=access;this.audit=audit;}
    @GetMapping public List<Setting> list(HttpServletRequest request){access.requireModule(actor(request),"settings");return jdbc.sql("SELECT setting_key,value::text,description,current_version FROM system_settings ORDER BY setting_key").query(Setting.class).list();}
    @PutMapping("/{key}") @Transactional public Setting update(@PathVariable String key,@RequestBody Change body,HttpServletRequest request){
        String employee=actor(request);access.requireSystemAdmin(employee);long user=jdbc.sql("SELECT id FROM users WHERE dingtalk_user_id=:employee").param("employee",employee).query(Long.class).single();
        Setting current=jdbc.sql("SELECT setting_key,value::text,description,current_version FROM system_settings WHERE setting_key=:key FOR UPDATE").param("key",key).query(Setting.class).single();
        int next=current.currentVersion()+1;
        jdbc.sql("UPDATE system_settings SET value=CAST(:value AS jsonb),current_version=:version,updated_by=:user,updated_at=now() WHERE setting_key=:key").param("value",body.value()).param("version",next).param("user",user).param("key",key).update();
        jdbc.sql("INSERT INTO system_setting_versions(setting_id,version_number,value,change_reason,changed_by) SELECT id,:version,CAST(:value AS jsonb),:reason,:user FROM system_settings WHERE setting_key=:key").param("version",next).param("value",body.value()).param("reason",body.reason()).param("user",user).param("key",key).update();
        audit.record(employee,"settings","update","system_setting",key,Map.of("version",next,"reason",body.reason()));return jdbc.sql("SELECT setting_key,value::text,description,current_version FROM system_settings WHERE setting_key=:key").param("key",key).query(Setting.class).single();
    }
    private String actor(HttpServletRequest request){return String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));}
    public record Setting(String settingKey,String value,String description,int currentVersion){}
    public record Change(String value,String reason){}
}
