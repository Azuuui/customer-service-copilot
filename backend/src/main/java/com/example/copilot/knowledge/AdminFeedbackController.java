package com.example.copilot.knowledge;

import com.example.copilot.audit.AuditService;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@ConditionalOnProperty(name="copilot.persistence",havingValue="jdbc")
public class AdminFeedbackController {
    private final JdbcClient jdbc;private final AuditService audit;
    public AdminFeedbackController(JdbcClient jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
    @GetMapping public List<Map<String,Object>> cases(@RequestParam(defaultValue="pending")String status,@RequestParam(defaultValue="10")int size){
        return jdbc.sql("SELECT id,latest_query_text,status,priority,report_count,updated_at FROM feedback_cases WHERE (:status='' OR status=:status) ORDER BY updated_at DESC LIMIT :size")
                .param("status",status).param("size",Math.min(Math.max(size,1),100)).query((rs,n)->Map.<String,Object>of("id",rs.getLong(1),"query",rs.getString(2),"status",rs.getString(3),"priority",rs.getString(4),"reportCount",rs.getInt(5),"updatedAt",rs.getTimestamp(6).toInstant())).list();
    }
    @GetMapping("/{id}/reports") public List<Map<String,Object>> reports(@PathVariable long id){return jdbc.sql("SELECT id,query_text,feedback_type,detail,created_at FROM feedback_reports WHERE feedback_case_id=:id ORDER BY created_at DESC")
            .param("id",id).query((rs,n)->{Map<String,Object> row=new java.util.HashMap<>();row.put("id",rs.getLong(1));row.put("query",rs.getString(2));row.put("type",rs.getString(3));row.put("detail",rs.getString(4));row.put("createdAt",rs.getTimestamp(5).toInstant());return row;}).list();}
    @PostMapping("/{id}/ignore") public Map<String,Object> ignore(@PathVariable long id,@RequestBody Reason body,HttpServletRequest request){return change(id,"closed",body.reason(),"ignore",request);}
    @PostMapping("/{id}/undo-ignore") public Map<String,Object> undo(@PathVariable long id,HttpServletRequest request){return change(id,"pending","撤销忽略","undo_ignore",request);}
    private Map<String,Object> change(long id,String status,String reason,String action,HttpServletRequest request){int changed=jdbc.sql("UPDATE feedback_cases SET status=:status,ignore_reason=:reason,ignored_at=CASE WHEN :status='closed' THEN now() ELSE NULL END,updated_at=now() WHERE id=:id")
            .param("status",status).param("reason",reason).param("id",id).update();audit.record(String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID)),"feedback",action,"feedback_case",String.valueOf(id),Map.of("reason",reason));return Map.of("updated",changed);}
    public record Reason(String reason){}
}
