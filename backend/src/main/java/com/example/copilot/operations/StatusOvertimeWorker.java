package com.example.copilot.operations;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StatusOvertimeWorker {
    private final JdbcClient jdbc;
    public StatusOvertimeWorker(JdbcClient jdbc){this.jdbc=jdbc;}
    @Scheduled(fixedDelayString="${copilot.status.overtime-scan-ms:30000}")
    @Transactional public void markOvertime(){
        var ids=jdbc.sql("UPDATE employee_current_statuses SET is_overtime=true,updated_at=now() WHERE status_code<>'working' AND is_overtime=false AND expected_end_at<=now() RETURNING status_request_id").query(Long.class).list();
        for(long id:ids){jdbc.sql("INSERT INTO status_request_events(status_request_id,event_type,event_data) VALUES(:id,'overtime','{\"action\":\"remind_only\"}'::jsonb)").param("id",id).update();}
    }
}
