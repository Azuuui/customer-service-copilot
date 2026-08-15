package com.example.copilot.operations;

import com.example.copilot.audit.AuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class StatusService {
    private final JdbcClient jdbc;private final AuditService audit;
    public StatusService(JdbcClient jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
    public List<Rule> rules(){return jdbc.sql("SELECT status_code,display_name,queue_name,requires_approval,capacity_limit,default_duration_minutes,minimum_duration_minutes,maximum_duration_minutes FROM status_type_rules WHERE is_active ORDER BY id").query(Rule.class).list();}
    public Dashboard dashboard(){
        List<QueueItem> shortQueue=queue("short_break"),longQueue=queue("long_break");
        List<RequestItem> requests=jdbc.sql(requestSelect()+" WHERE r.request_status IN ('pending','active') ORDER BY r.requested_at DESC LIMIT 10").query(RequestItem.class).list();
        List<CurrentStatus> statuses=jdbc.sql("SELECT u.dingtalk_user_id employee_id,u.name employee_name,c.status_code,c.started_at,c.expected_end_at,c.is_overtime overtime FROM employee_current_statuses c JOIN users u ON u.id=c.user_id ORDER BY c.updated_at DESC LIMIT 10").query(CurrentStatus.class).list();
        return new Dashboard(shortQueue,longQueue,requests,statuses,new Metrics(shortQueue.size(),longQueue.size(),requests.stream().filter(r->"active".equals(r.requestStatus())).count(),statuses.stream().filter(CurrentStatus::overtime).count()));
    }
    public Dashboard dashboardFor(String employee){
        Dashboard all=dashboard();
        List<CurrentStatus> own=jdbc.sql("SELECT u.dingtalk_user_id employee_id,u.name employee_name,c.status_code,c.started_at,c.expected_end_at,c.is_overtime overtime FROM employee_current_statuses c JOIN users u ON u.id=c.user_id WHERE u.dingtalk_user_id=:employee")
                .param("employee",employee).query(CurrentStatus.class).list();
        List<RequestItem> requests=all.requests().stream().filter(item->employee.equals(item.employeeId())).toList();
        return new Dashboard(all.shortBreakQueue(),all.longBreakQueue(),requests,own,all.metrics());
    }
    private List<QueueItem> queue(String name){return jdbc.sql("SELECT r.id,u.dingtalk_user_id employee_id,u.name employee_name,r.requested_duration_minutes,ROW_NUMBER() OVER(ORDER BY COALESCE(r.position_override,2147483647),r.queued_at,r.id)::int position FROM status_requests r JOIN users u ON u.id=r.user_id WHERE r.queue_name=:queue AND r.request_status='pending' ORDER BY position") .param("queue",name).query(QueueItem.class).list();}
    public List<RequestItem> history(int page,int size){return jdbc.sql(requestSelect()+" ORDER BY r.requested_at DESC LIMIT :size OFFSET :offset").param("size",size).param("offset",page*size).query(RequestItem.class).list();}

    @Transactional public long request(String employee,long actor,String targetEmployee,RequestCommand command,boolean adminCreated){
        long user=userId(targetEmployee==null?employee:targetEmployee); Rule rule=rule(command.statusCode());
        int duration=command.durationMinutes()==null?(rule.defaultDurationMinutes()==null?10:rule.defaultDurationMinutes()):command.durationMinutes();
        if(rule.minimumDurationMinutes()!=null&&duration<rule.minimumDurationMinutes()||rule.maximumDurationMinutes()!=null&&duration>rule.maximumDurationMinutes())throw new IllegalArgumentException("申请时长不在允许范围内");
        long id=jdbc.sql("INSERT INTO status_requests(user_id,status_type_rule_id,queue_name,requested_duration_minutes,queued_at,created_by_admin) SELECT :user,id,queue_name,:duration,CASE WHEN queue_name IS NOT NULL THEN now() END,:admin FROM status_type_rules WHERE status_code=:code RETURNING id")
                .param("user",user).param("duration",duration).param("admin",adminCreated).param("code",command.statusCode()).query(Long.class).single();
        event(id,actor,"requested",Map.of("duration",duration));audit.record(employee,"status_requests","request","status_request",String.valueOf(id));
        if(adminCreated) approve(employee,actor,id,true,"管理员直接安排"); return id;
    }
    @Transactional public RequestItem approve(String employee,long actor,long id,boolean allowOverCapacity,String reason){
        Pending pending=jdbc.sql("SELECT r.user_id,t.status_code,t.queue_name,t.capacity_limit,r.requested_duration_minutes FROM status_requests r JOIN status_type_rules t ON t.id=r.status_type_rule_id WHERE r.id=:id AND r.request_status='pending' FOR UPDATE").param("id",id).query(Pending.class).single();
        long active=pending.queueName()==null?0:jdbc.sql("SELECT count(*) FROM status_requests WHERE queue_name=:queue AND request_status='active'").param("queue",pending.queueName()).query(Long.class).single();
        boolean over=pending.capacityLimit()!=null&&active>=pending.capacityLimit();
        if(over&&!allowOverCapacity)throw new IllegalArgumentException("队列已满，需填写原因并超额批准");
        endCurrent(pending.userId(),actor,"新状态自动结束旧状态");
        jdbc.sql("UPDATE status_requests SET request_status='active',decided_at=now(),decided_by=:actor,decision_reason=:reason,started_at=now(),expected_end_at=now()+make_interval(mins=>:duration),is_over_capacity=:over,over_capacity_reason=CASE WHEN :over THEN :reason END,revision=revision+1 WHERE id=:id")
                .param("actor",actor).param("reason",reason).param("duration",pending.requestedDurationMinutes()).param("over",over).param("id",id).update();
        jdbc.sql("INSERT INTO employee_current_statuses(user_id,status_code,status_request_id,started_at,expected_end_at,is_overtime,updated_at) SELECT user_id,:code,id,started_at,expected_end_at,false,now() FROM status_requests WHERE id=:id ON CONFLICT(user_id) DO UPDATE SET status_code=:code,status_request_id=EXCLUDED.status_request_id,started_at=EXCLUDED.started_at,expected_end_at=EXCLUDED.expected_end_at,is_overtime=false,updated_at=now()")
                .param("code",pending.statusCode()).param("id",id).update();
        jdbc.sql("INSERT INTO employee_status_history(user_id,status_request_id,status_code,started_at,expected_end_at) SELECT user_id,id,:code,started_at,expected_end_at FROM status_requests WHERE id=:id").param("code",pending.statusCode()).param("id",id).update();
        event(id,actor,"approved",Map.of("overCapacity",over));event(id,actor,"started",Map.of());audit.record(employee,"status_requests","approve","status_request",String.valueOf(id),Map.of("overCapacity",over));return find(id);
    }
    @Transactional public RequestItem reorder(String employee,long actor,long id,int position,String reason){
        jdbc.sql("UPDATE status_requests SET position_override=:position,position_override_reason=:reason,revision=revision+1 WHERE id=:id AND request_status='pending' AND queue_name IS NOT NULL").param("position",position).param("reason",reason).param("id",id).update();
        event(id,actor,"reordered",Map.of("position",position,"reason",reason));audit.record(employee,"status_requests","reorder","status_request",String.valueOf(id));return find(id);
    }
    @Transactional public void end(String employee,long actor,long id,String reason){
        Pending p=jdbc.sql("SELECT r.user_id,t.status_code,t.queue_name,t.capacity_limit,r.requested_duration_minutes FROM status_requests r JOIN status_type_rules t ON t.id=r.status_type_rule_id WHERE r.id=:id AND r.request_status='active' FOR UPDATE").param("id",id).query(Pending.class).single();
        jdbc.sql("UPDATE status_requests SET request_status='ended',ended_at=now(),ended_by=:actor,end_reason=:reason,revision=revision+1 WHERE id=:id").param("actor",actor).param("reason",reason).param("id",id).update();
        jdbc.sql("UPDATE employee_status_history SET ended_at=now(),ended_by=:actor,end_reason=:reason,was_overtime=COALESCE((SELECT is_overtime FROM employee_current_statuses WHERE user_id=:user),false) WHERE status_request_id=:id AND ended_at IS NULL").param("actor",actor).param("reason",reason).param("user",p.userId()).param("id",id).update();
        setWorking(p.userId());event(id,actor,"ended",Map.of("reason",reason));audit.record(employee,"status_requests","end","status_request",String.valueOf(id));
    }
    private void endCurrent(long user,long actor,String reason){
        List<Long> active=jdbc.sql("SELECT status_request_id FROM employee_current_statuses WHERE user_id=:user AND status_request_id IS NOT NULL FOR UPDATE").param("user",user).query(Long.class).list();
        for(long id:active){jdbc.sql("UPDATE status_requests SET request_status='ended',ended_at=now(),ended_by=:actor,end_reason=:reason WHERE id=:id AND request_status='active'").param("actor",actor).param("reason",reason).param("id",id).update();jdbc.sql("UPDATE employee_status_history SET ended_at=now(),ended_by=:actor,end_reason=:reason,was_overtime=COALESCE((SELECT is_overtime FROM employee_current_statuses WHERE user_id=:user),false) WHERE status_request_id=:id AND ended_at IS NULL").param("actor",actor).param("reason",reason).param("user",user).param("id",id).update();event(id,actor,"ended",Map.of("reason",reason));}
    }
    private void setWorking(long user){jdbc.sql("INSERT INTO employee_current_statuses(user_id,status_code,status_request_id,started_at,expected_end_at,is_overtime,updated_at) VALUES(:user,'working',NULL,now(),NULL,false,now()) ON CONFLICT(user_id) DO UPDATE SET status_code='working',status_request_id=NULL,started_at=now(),expected_end_at=NULL,is_overtime=false,updated_at=now()").param("user",user).update();}
    private void event(long request,long actor,String type,Map<String,Object> data){jdbc.sql("INSERT INTO status_request_events(status_request_id,actor_user_id,event_type,event_data) VALUES(:request,:actor,:type,CAST(:data AS jsonb))").param("request",request).param("actor",actor).param("type",type).param("data",json(data)).update();}
    private String json(Map<String,Object> data){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);}catch(Exception e){throw new IllegalStateException(e);}}
    private long userId(String employee){return jdbc.sql("SELECT id FROM users WHERE dingtalk_user_id=:employee").param("employee",employee).query(Long.class).single();}
    private Rule rule(String code){return jdbc.sql("SELECT status_code,display_name,queue_name,requires_approval,capacity_limit,default_duration_minutes,minimum_duration_minutes,maximum_duration_minutes FROM status_type_rules WHERE status_code=:code AND is_active").param("code",code).query(Rule.class).single();}
    private RequestItem find(long id){return jdbc.sql(requestSelect()+" WHERE r.id=:id").param("id",id).query(RequestItem.class).single();}
    private String requestSelect(){return "SELECT r.id,u.dingtalk_user_id employee_id,u.name employee_name,t.status_code,t.display_name,r.queue_name,r.request_status,r.requested_duration_minutes,r.is_over_capacity over_capacity,r.requested_at,r.started_at,r.expected_end_at,r.ended_at FROM status_requests r JOIN users u ON u.id=r.user_id JOIN status_type_rules t ON t.id=r.status_type_rule_id";}
    public record Rule(String statusCode,String displayName,String queueName,boolean requiresApproval,Integer capacityLimit,Integer defaultDurationMinutes,Integer minimumDurationMinutes,Integer maximumDurationMinutes){}
    private record Pending(long userId,String statusCode,String queueName,Integer capacityLimit,int requestedDurationMinutes){}
    public record RequestCommand(String statusCode,Integer durationMinutes){}
    public record QueueItem(long id,String employeeId,String employeeName,int requestedDurationMinutes,int position){}
    public record RequestItem(long id,String employeeId,String employeeName,String statusCode,String displayName,String queueName,String requestStatus,int requestedDurationMinutes,boolean overCapacity,Instant requestedAt,Instant startedAt,Instant expectedEndAt,Instant endedAt){}
    public record CurrentStatus(String employeeId,String employeeName,String statusCode,Instant startedAt,Instant expectedEndAt,boolean overtime){}
    public record Metrics(long shortBreakWaiting,long longBreakWaiting,long active,long overtime){}
    public record Dashboard(List<QueueItem> shortBreakQueue,List<QueueItem> longBreakQueue,List<RequestItem> requests,List<CurrentStatus> currentStatuses,Metrics metrics){}
}
