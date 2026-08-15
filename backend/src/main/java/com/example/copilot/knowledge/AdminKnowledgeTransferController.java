package com.example.copilot.knowledge;

import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.example.copilot.audit.AuditService;

@RestController
@RequestMapping("/api/v1/admin/knowledge-transfer")
@ConditionalOnProperty(name="copilot.persistence",havingValue="jdbc")
public class AdminKnowledgeTransferController {
    private static final List<String> FIELDS=List.of("source_key","standard_question","category","user_questions","keywords","original_answer","status","valid_from","valid_to","version");
    private final KnowledgeService knowledge;private final JdbcClient jdbc;private final AuditService audit;
    public AdminKnowledgeTransferController(KnowledgeService knowledge,JdbcClient jdbc,AuditService audit){this.knowledge=knowledge;this.jdbc=jdbc;this.audit=audit;}

    @PostMapping("/preflight") public Map<String,Object> preflight(@RequestBody List<AdminKnowledgeController.SaveRequest> rows){
        List<String> duplicates=duplicates(rows);return Map.of("valid",duplicates.isEmpty(),"rows",rows.size(),"duplicates",duplicates);
    }
    @PostMapping("/import") @Transactional public Map<String,Object> importRows(@RequestBody List<AdminKnowledgeController.SaveRequest> rows,HttpServletRequest request){
        List<String> duplicates=duplicates(rows);if(!duplicates.isEmpty())throw new IllegalArgumentException("file_duplicate_unique_keys");
        String actor=String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));
        for(AdminKnowledgeController.SaveRequest row:rows)knowledge.save(actor,new KnowledgeService.SaveCommand(null,row.category(),row.standardQuestion(),row.userQuestions(),row.keywords(),row.originalAnswer(),row.validFrom(),row.validTo(),row.reason()));
        audit.record(actor,"knowledge","import","knowledge_batch",String.valueOf(rows.size()));
        return Map.of("imported",rows.size(),"indexStatus","pending");
    }
    @GetMapping(value="/export",produces="text/csv") public ResponseEntity<byte[]> export(@RequestParam(defaultValue="current")String scope,@RequestParam(defaultValue="")String query,HttpServletRequest request){
        List<List<String>> rows="history".equals(scope)?historyRows():currentRows(query);
        StringBuilder csv=new StringBuilder("\uFEFF").append(String.join(",",FIELDS)).append('\n');
        rows.forEach(row->csv.append(row.stream().map(this::escape).reduce((a,b)->a+","+b).orElse("")).append('\n'));
        String actor=String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));
        audit.record(actor,"knowledge","export","knowledge_export",scope,Map.of("rows",rows.size(),"query",query));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=knowledge-"+scope+".csv")
                .contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private List<String> duplicates(List<AdminKnowledgeController.SaveRequest> rows){Set<String> seen=new HashSet<>();List<String> duplicates=new ArrayList<>();for(var row:rows){String key=row.category().trim()+"\u0000"+row.standardQuestion().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+","");if(!seen.add(key))duplicates.add(row.category()+" / "+row.standardQuestion());}return duplicates;}
    private List<List<String>> currentRows(String query){return jdbc.sql("SELECT source_key,standard_question,category,user_questions::text,keywords::text,original_answer,CASE WHEN is_active THEN 'active' ELSE 'disabled' END,valid_from::text,valid_to::text,current_version FROM knowledge_items WHERE is_active AND (valid_from IS NULL OR valid_from<=now()) AND (valid_to IS NULL OR valid_to>now()) AND (:query='' OR standard_question ILIKE '%'||:query||'%' OR category ILIKE '%'||:query||'%') ORDER BY category,standard_question")
            .param("query",query).query((rs,n)->row(rs,1)).list();}
    private List<List<String>> historyRows(){return jdbc.sql("SELECT e.source_key,v.standard_question,k.category,v.user_questions::text,v.keywords::text,v.original_answer,e.lifecycle_status,v.valid_from::text,v.valid_to::text,v.version_number FROM knowledge_versions v JOIN knowledge_entries e ON e.id=v.knowledge_entry_id JOIN knowledge_items k ON k.source_key=e.source_key ORDER BY e.source_key,v.version_number")
            .query((rs,n)->row(rs,1)).list();}
    private List<String> row(java.sql.ResultSet rs,int ignored)throws java.sql.SQLException{List<String> values=new ArrayList<>();for(int i=1;i<=10;i++)values.add(Objects.toString(rs.getObject(i),""));return values;}
    private String escape(String value){return "\""+value.replace("\"","\"\"")+"\"";}
}
