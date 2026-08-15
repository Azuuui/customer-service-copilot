package com.example.copilot.knowledge;

import com.example.copilot.query.RetrievalClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name="copilot.persistence", havingValue="jdbc")
public class KnowledgeReindexWorker {
    private final JdbcClient jdbc;
    private final RetrievalClient retrieval;
    public KnowledgeReindexWorker(JdbcClient jdbc, RetrievalClient retrieval){this.jdbc=jdbc;this.retrieval=retrieval;}
    @Scheduled(fixedDelayString="${copilot.knowledge.reindex-delay-ms:15000}")
    public void processPending(){
        List<String> keys=jdbc.sql("SELECT DISTINCT source_key FROM knowledge_reindex_events WHERE event_status='pending' ORDER BY source_key LIMIT 20")
                .query(String.class).list();
        for(String key:keys){
            if(retrieval.reindex(key)) jdbc.sql("UPDATE knowledge_reindex_events SET event_status='completed',completed_at=now() WHERE source_key=:key AND event_status='pending'").param("key",key).update();
        }
    }
}
