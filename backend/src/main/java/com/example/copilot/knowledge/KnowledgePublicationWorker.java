package com.example.copilot.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name="copilot.persistence",havingValue="jdbc")
public class KnowledgePublicationWorker {
    private final JdbcClient jdbc;
    public KnowledgePublicationWorker(JdbcClient jdbc){this.jdbc=jdbc;}
    @Scheduled(fixedDelayString="${copilot.knowledge.publication-delay-ms:10000}")
    @Transactional
    public void publishDue(){
        List<Long> due=jdbc.sql("SELECT id FROM knowledge_publication_schedule WHERE publication_status='scheduled' AND publish_at<=now() ORDER BY publish_at LIMIT 20").query(Long.class).list();
        for(Long scheduleId:due){
            jdbc.sql("""
                    UPDATE knowledge_items k SET standard_question=v.standard_question,
                      standard_question_normalized=lower(regexp_replace(v.standard_question,'\\s+','','g')),
                      user_questions=v.user_questions,keywords=v.keywords,original_answer=v.original_answer,
                      search_text=v.search_text,search_tokens='[]',term_frequencies='{}',document_length=0,
                      search_vector=v.search_vector,is_active=true,valid_from=v.valid_from,valid_to=v.valid_to,
                      content_hash=v.content_hash,change_reason=v.change_reason,embedding=NULL,updated_at=now()
                    FROM knowledge_publication_schedule s JOIN knowledge_versions v ON v.id=s.knowledge_version_id
                    JOIN knowledge_entries e ON e.id=v.knowledge_entry_id
                    WHERE s.id=:id AND k.source_key=e.source_key
                    """).param("id",scheduleId).update();
            jdbc.sql("UPDATE knowledge_publication_schedule SET publication_status='published',published_at=now(),updated_at=now() WHERE id=:id")
                    .param("id",scheduleId).update();
        }
    }
}
