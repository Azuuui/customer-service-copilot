package com.example.copilot.knowledge;

import com.example.copilot.audit.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@ConditionalOnProperty(name="copilot.persistence", havingValue="jdbc")
public class JdbcKnowledgeService implements KnowledgeService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final AuditService audit;
    public JdbcKnowledgeService(JdbcClient jdbc, ObjectMapper json, AuditService audit) { this.jdbc=jdbc; this.json=json; this.audit=audit; }

    @Override public Page list(String query, String status, int page, int size) {
        String term = query == null ? "" : query.trim(); String state = status == null ? "" : status.trim();
        long total = jdbc.sql("""
                SELECT count(*) FROM knowledge_items WHERE
                (:term='' OR standard_question ILIKE '%%'||:term||'%%' OR category ILIKE '%%'||:term||'%%')
                AND (:state='' OR CASE WHEN is_active THEN 'active' ELSE 'disabled' END=:state)
                """)
                .param("term",term).param("state",state).query(Long.class).single();
        List<Summary> items = jdbc.sql("""
                SELECT k.source_key,k.standard_question,k.category,
                CASE WHEN k.is_active THEN 'active' ELSE 'disabled' END,cv.version_number,
                k.embedding IS NOT NULL,k.valid_from,k.valid_to FROM knowledge_items k
                JOIN knowledge_entries e ON e.source_key=k.source_key
                JOIN knowledge_versions cv ON cv.id=e.current_version_id WHERE
                (:term='' OR k.standard_question ILIKE '%%'||:term||'%%' OR k.category ILIKE '%%'||:term||'%%')
                AND (:state='' OR CASE WHEN k.is_active THEN 'active' ELSE 'disabled' END=:state)
                ORDER BY k.updated_at DESC, k.id DESC LIMIT :size OFFSET :offset
                """)
                .param("term",term).param("state",state).param("size",size).param("offset",page*size)
                .query((rs,n)->new Summary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getInt(5),rs.getBoolean(6),instant(rs.getTimestamp(7)),instant(rs.getTimestamp(8)))).list();
        return new Page(items,total,page,size);
    }

    @Override public Detail detail(String sourceKey) {
        Summary item = jdbc.sql("""
                SELECT k.source_key,k.standard_question,k.category,
                CASE WHEN k.is_active THEN 'active' ELSE 'disabled' END,cv.version_number,
                k.embedding IS NOT NULL,k.valid_from,k.valid_to FROM knowledge_items k
                JOIN knowledge_entries e ON e.source_key=k.source_key
                JOIN knowledge_versions cv ON cv.id=e.current_version_id WHERE k.source_key=:key
                """)
                .param("key",sourceKey).query((rs,n)->new Summary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getInt(5),rs.getBoolean(6),instant(rs.getTimestamp(7)),instant(rs.getTimestamp(8)))).optional().orElseThrow(NoSuchElementException::new);
        List<Version> versions = jdbc.sql("""
                SELECT v.version_number,v.standard_question,v.user_questions,
                v.keywords,v.original_answer,v.valid_from,v.valid_to,v.change_reason,v.created_at
                FROM knowledge_versions v JOIN knowledge_entries e ON e.id=v.knowledge_entry_id
                WHERE e.source_key=:key ORDER BY v.version_number DESC
                """).param("key",sourceKey)
                .query((rs,n)->new Version(rs.getInt(1),rs.getString(2),strings(rs.getString(3)),strings(rs.getString(4)),
                        rs.getString(5),instant(rs.getTimestamp(6)),instant(rs.getTimestamp(7)),rs.getString(8),rs.getTimestamp(9).toInstant())).list();
        return new Detail(item,versions);
    }

    @Override @Transactional
    public Detail save(String actor, SaveCommand command) {
        validate(command);
        String sourceKey = command.sourceKey()==null || command.sourceKey().isBlank() ? "managed-"+UUID.randomUUID() : command.sourceKey();
        boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_items WHERE source_key=:key)")
                .param("key",sourceKey).query(Boolean.class).single();
        if (exists && command.validFrom()!=null && command.validFrom().isAfter(Instant.now().plusSeconds(1))) {
            scheduleFuture(actor, sourceKey, command);
            return detail(sourceKey);
        }
        String normalized = normalize(command.standardQuestion());
        Long duplicate = jdbc.sql("""
                SELECT count(*) FROM knowledge_items WHERE category=:category
                AND standard_question_normalized=:question AND source_key<>:key
                """)
                .param("category",command.category()).param("question",normalized).param("key",sourceKey).query(Long.class).single();
        if (duplicate>0) throw new DataIntegrityViolationException("knowledge_unique_key_conflict");
        String searchText = String.join("\n", command.standardQuestion(), String.join("\n", safe(command.userQuestions())),
                String.join("\n", safe(command.keywords())), command.category(), command.originalAnswer());
        String contentHash = hash(Map.of("question",command.standardQuestion(),"category",command.category(),
                "userQuestions",safe(command.userQuestions()),"keywords",safe(command.keywords()),"answer",command.originalAnswer(),
                "validFrom",String.valueOf(command.validFrom()),"validTo",String.valueOf(command.validTo()),"nonce",UUID.randomUUID().toString()));
        jdbc.sql("""
                INSERT INTO knowledge_items(source_key,standard_question,standard_question_normalized,category,
                user_questions,keywords,scenarios,original_answer,search_text,search_tokens,term_frequencies,
                document_length,search_vector,is_active,valid_from,valid_to,source_updated_by,content_hash,change_reason,embedding)
                VALUES (:key,:question,:normalized,:category,CAST(:userQuestions AS jsonb),CAST(:keywords AS jsonb),'[]',
                :answer,:searchText,'[]','{}',0,to_tsvector('simple',:searchText),true,:validFrom,:validTo,:actor,:hash,:reason,NULL)
                ON CONFLICT(source_key) DO UPDATE SET standard_question=excluded.standard_question,
                standard_question_normalized=excluded.standard_question_normalized,category=excluded.category,
                user_questions=excluded.user_questions,keywords=excluded.keywords,original_answer=excluded.original_answer,
                search_text=excluded.search_text,search_tokens='[]',term_frequencies='{}',document_length=0,
                search_vector=excluded.search_vector,is_active=true,valid_from=excluded.valid_from,valid_to=excluded.valid_to,
                source_updated_by=excluded.source_updated_by,content_hash=excluded.content_hash,change_reason=excluded.change_reason,current_version=knowledge_items.current_version+1,
                embedding=NULL,embedding_model=NULL,embedding_model_version=NULL,embedding_dimension=NULL,
                embedding_generated_at=NULL,updated_at=now()
                """)
                .param("key",sourceKey).param("question",command.standardQuestion().trim()).param("normalized",normalized)
                .param("category",command.category().trim()).param("userQuestions",write(safe(command.userQuestions())))
                .param("keywords",write(safe(command.keywords()))).param("answer",command.originalAnswer())
                .param("searchText",searchText).param("validFrom",timestamp(command.validFrom())).param("validTo",timestamp(command.validTo()))
                .param("actor",actor).param("hash",contentHash).param("reason",command.reason()).update();
        audit.record(actor,"knowledge","save","knowledge",sourceKey,Map.of("reason",command.reason()));
        return detail(sourceKey);
    }

    @Override @Transactional
    public Detail rollback(String actor, String sourceKey, int version, String reason) {
        if (reason==null || reason.isBlank()) throw new IllegalArgumentException("rollback_reason_required");
        Version target = detail(sourceKey).versions().stream().filter(item->item.version()==version).findFirst().orElseThrow(NoSuchElementException::new);
        String category = detail(sourceKey).knowledge().category();
        Detail result = save(actor,new SaveCommand(sourceKey,category,target.standardQuestion(),target.userQuestions(),target.keywords(),
                target.originalAnswer(),target.validFrom(),target.validTo(),"回滚至 v"+version+"："+reason));
        audit.record(actor,"knowledge","rollback","knowledge",sourceKey,Map.of("targetVersion",version,"reason",reason));
        return result;
    }

    @Override @Transactional
    public Detail disable(String actor, String sourceKey, String reason) {
        if (reason==null || reason.isBlank()) throw new IllegalArgumentException("disable_reason_required");
        if (jdbc.sql("UPDATE knowledge_items SET is_active=false,updated_at=now() WHERE source_key=:key").param("key",sourceKey).update()!=1)
            throw new NoSuchElementException();
        audit.record(actor,"knowledge","disable","knowledge",sourceKey,Map.of("reason",reason));
        return detail(sourceKey);
    }

    private void validate(SaveCommand c) {
        if(c.category()==null||c.category().isBlank()||c.standardQuestion()==null||c.standardQuestion().isBlank()||c.originalAnswer()==null||c.originalAnswer().isBlank()) throw new IllegalArgumentException("knowledge_required_fields");
        if(c.reason()==null||c.reason().isBlank()) throw new IllegalArgumentException("change_reason_required");
        if(c.validFrom()!=null&&c.validTo()!=null&&!c.validTo().isAfter(c.validFrom())) throw new IllegalArgumentException("invalid_validity_window");
    }
    private void scheduleFuture(String actor,String sourceKey,SaveCommand command){
        String searchText=String.join("\n",command.standardQuestion(),String.join("\n",safe(command.userQuestions())),String.join("\n",safe(command.keywords())),command.category(),command.originalAnswer());
        String contentHash=hash(Map.of("scheduled",UUID.randomUUID().toString(),"question",command.standardQuestion(),"answer",command.originalAnswer()));
        Long versionId=jdbc.sql("""
                INSERT INTO knowledge_versions(knowledge_entry_id,version_number,standard_question,user_questions,keywords,
                    original_answer,answer_blocks,search_text,search_tokens,term_frequencies,document_length,search_vector,
                    valid_from,valid_to,content_hash,change_reason)
                SELECT e.id,COALESCE(max(v.version_number),0)+1,:question,CAST(:userQuestions AS jsonb),CAST(:keywords AS jsonb),
                    :answer,'[]',:searchText,'[]','{}',0,to_tsvector('simple',:searchText),:validFrom,:validTo,:hash,:reason
                FROM knowledge_entries e LEFT JOIN knowledge_versions v ON v.knowledge_entry_id=e.id
                WHERE e.source_key=:key GROUP BY e.id RETURNING id
                """).param("question",command.standardQuestion()).param("userQuestions",write(safe(command.userQuestions())))
                .param("keywords",write(safe(command.keywords()))).param("answer",command.originalAnswer()).param("searchText",searchText)
                .param("validFrom",timestamp(command.validFrom())).param("validTo",timestamp(command.validTo())).param("hash",contentHash)
                .param("reason",command.reason()).param("key",sourceKey).query(Long.class).single();
        jdbc.sql("INSERT INTO knowledge_publication_schedule(knowledge_version_id,publication_status,publish_at,retire_at,scheduled_by) VALUES (:version,'scheduled',:publishAt,:retireAt,(SELECT id FROM users WHERE dingtalk_user_id=:actor))")
                .param("version",versionId).param("publishAt",timestamp(command.validFrom())).param("retireAt",timestamp(command.validTo())).param("actor",actor).update();
        audit.record(actor,"knowledge","schedule","knowledge",sourceKey,Map.of("publishAt",command.validFrom().toString(),"reason",command.reason()));
    }
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    private List<String> safe(List<String> value){return value==null?List.of():value.stream().map(String::trim).filter(v->!v.isBlank()).distinct().toList();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
    private List<String> strings(String value){try{return json.readValue(value,json.getTypeFactory().constructCollectionType(List.class,String.class));}catch(Exception e){throw new IllegalArgumentException(e);}}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Instant instant(Timestamp value){return value==null?null:value.toInstant();}
    private Timestamp timestamp(Instant value){return value==null?null:Timestamp.from(value);}
}
