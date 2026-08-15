package com.example.copilot.knowledge;

import com.example.copilot.audit.AuditService;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/admin/taxonomy")
@ConditionalOnProperty(name="copilot.persistence", havingValue="jdbc")
public class AdminTaxonomyController {
    private final JdbcClient jdbc; private final AuditService audit; private final ObjectMapper json;
    public AdminTaxonomyController(JdbcClient jdbc,AuditService audit,ObjectMapper json){this.jdbc=jdbc;this.audit=audit;this.json=json;}
    @GetMapping("/categories") public List<Category> categories(){return jdbc.sql("SELECT id,parent_id,name,depth,sort_order,is_active FROM knowledge_categories ORDER BY depth,parent_id NULLS FIRST,sort_order,id")
            .query((rs,n)->new Category(rs.getLong(1),(Long)rs.getObject(2),rs.getString(3),rs.getInt(4),rs.getInt(5),rs.getBoolean(6))).list();}
    @PostMapping("/categories") public Category createCategory(@RequestBody CategoryRequest body,HttpServletRequest request){
        int depth=body.parentId()==null?1:jdbc.sql("SELECT depth+1 FROM knowledge_categories WHERE id=:id").param("id",body.parentId()).query(Integer.class).single();
        Long id=jdbc.sql("INSERT INTO knowledge_categories(parent_id,name,normalized_name,depth,sort_order) VALUES (:parent,:name,lower(trim(:name)),:depth,:sort) RETURNING id")
                .param("parent",body.parentId()).param("name",body.name()).param("depth",depth).param("sort",body.sortOrder()).query(Long.class).single();
        audit.record(actor(request),"taxonomy","create","category",String.valueOf(id));
        return new Category(id,body.parentId(),body.name(),depth,body.sortOrder(),true);
    }
    @GetMapping("/tags") public List<Tag> tags(){return jdbc.sql("SELECT id,name,color,is_active FROM knowledge_tags ORDER BY name")
            .query((rs,n)->new Tag(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4))).list();}
    @PostMapping("/tags") public Tag createTag(@RequestBody TagRequest body,HttpServletRequest request){
        Long id=jdbc.sql("INSERT INTO knowledge_tags(name,normalized_name,color) VALUES (:name,lower(trim(:name)),:color) RETURNING id")
                .param("name",body.name()).param("color",body.color()).query(Long.class).single();
        audit.record(actor(request),"taxonomy","create","tag",String.valueOf(id));return new Tag(id,body.name(),body.color(),true);
    }
    @PostMapping("/bulk-move") @Transactional public Map<String,Object> bulkMove(@RequestBody BulkMove body,HttpServletRequest request){
        if(body.sourceKeys()==null||body.sourceKeys().isEmpty())throw new IllegalArgumentException("source_keys_required");
        List<Map<String,String>> snapshot=body.sourceKeys().stream().map(key->Map.of("sourceKey",key,"category",jdbc.sql("SELECT category FROM knowledge_items WHERE source_key=:key").param("key",key).query(String.class).single())).toList();
        long operationId=saveOperation("move_category",snapshot,actor(request));int count=0;
        for(String key:body.sourceKeys())count+=jdbc.sql("UPDATE knowledge_items SET category=:category,content_hash=md5(content_hash||:category||clock_timestamp()::text),change_reason='批量移动类目',embedding=NULL,updated_at=now() WHERE source_key=:key").param("category",body.category()).param("key",key).update();
        audit.record(actor(request),"taxonomy","bulk_move","knowledge",String.valueOf(operationId),Map.of("count",count,"category",body.category()));
        return Map.of("updated",count,"operationId",operationId);
    }
    @PostMapping("/bulk-tags") @Transactional public Map<String,Object> bulkTags(@RequestBody BulkTags body,HttpServletRequest request){
        List<Map<String,Object>> snapshot=new java.util.ArrayList<>();int count=0;
        for(String key:body.sourceKeys()){
            Long versionId=jdbc.sql("SELECT current_version_id FROM knowledge_entries WHERE source_key=:key").param("key",key).query(Long.class).single();
            List<Long> old=jdbc.sql("SELECT tag_id FROM knowledge_version_tags WHERE knowledge_version_id=:id").param("id",versionId).query(Long.class).list();snapshot.add(Map.of("sourceKey",key,"tagIds",old));
            jdbc.sql("DELETE FROM knowledge_version_tags WHERE knowledge_version_id=:id").param("id",versionId).update();for(Long tagId:body.tagIds())jdbc.sql("INSERT INTO knowledge_version_tags(knowledge_version_id,tag_id) VALUES (:version,:tag) ON CONFLICT DO NOTHING").param("version",versionId).param("tag",tagId).update();count++;
        }
        long operationId=saveOperation("replace_tags",snapshot,actor(request));audit.record(actor(request),"taxonomy","bulk_tags","knowledge",String.valueOf(operationId),Map.of("count",count));return Map.of("updated",count,"operationId",operationId);
    }
    @PostMapping("/bulk-operations/{id}/undo") @Transactional public Map<String,Object> undo(@PathVariable long id,HttpServletRequest request)throws Exception{
        var row=jdbc.sql("SELECT operation_type,snapshot::text,undone_at IS NOT NULL FROM knowledge_bulk_operations WHERE id=:id").param("id",id).query((rs,n)->Map.of("type",rs.getString(1),"snapshot",rs.getString(2),"undone",rs.getBoolean(3))).single();
        if((Boolean)row.get("undone"))throw new IllegalArgumentException("operation_already_undone");JsonNode entries=json.readTree((String)row.get("snapshot"));
        if("move_category".equals(row.get("type")))for(JsonNode entry:entries)jdbc.sql("UPDATE knowledge_items SET category=:category,content_hash=md5(content_hash||:category||clock_timestamp()::text),change_reason='撤销批量移动',embedding=NULL,updated_at=now() WHERE source_key=:key").param("category",entry.path("category").asText()).param("key",entry.path("sourceKey").asText()).update();
        else for(JsonNode entry:entries){Long versionId=jdbc.sql("SELECT current_version_id FROM knowledge_entries WHERE source_key=:key").param("key",entry.path("sourceKey").asText()).query(Long.class).single();jdbc.sql("DELETE FROM knowledge_version_tags WHERE knowledge_version_id=:id").param("id",versionId).update();for(JsonNode tag:entry.path("tagIds"))jdbc.sql("INSERT INTO knowledge_version_tags(knowledge_version_id,tag_id) VALUES (:version,:tag) ON CONFLICT DO NOTHING").param("version",versionId).param("tag",tag.asLong()).update();}
        jdbc.sql("UPDATE knowledge_bulk_operations SET undone_at=now(),undone_by=(SELECT id FROM users WHERE dingtalk_user_id=:actor) WHERE id=:id").param("actor",actor(request)).param("id",id).update();audit.record(actor(request),"taxonomy","undo_bulk","knowledge",String.valueOf(id));return Map.of("undone",true);
    }
    private long saveOperation(String type,Object snapshot,String actor){try{return jdbc.sql("INSERT INTO knowledge_bulk_operations(operation_type,snapshot,created_by) VALUES (:type,CAST(:snapshot AS jsonb),(SELECT id FROM users WHERE dingtalk_user_id=:actor)) RETURNING id").param("type",type).param("snapshot",json.writeValueAsString(snapshot)).param("actor",actor).query(Long.class).single();}catch(Exception exception){throw new IllegalArgumentException("bulk_snapshot_invalid",exception);}}
    private String actor(HttpServletRequest r){return String.valueOf(r.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));}
    public record Category(long id,Long parentId,String name,int depth,int sortOrder,boolean active){}
    public record CategoryRequest(Long parentId,@NotBlank String name,int sortOrder){}
    public record Tag(long id,String name,String color,boolean active){}
    public record TagRequest(@NotBlank String name,String color){}
    public record BulkMove(List<String> sourceKeys,@NotBlank String category){}
    public record BulkTags(List<String> sourceKeys,List<Long> tagIds){}
}
