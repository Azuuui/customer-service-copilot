package com.example.copilot.operations;

import com.example.copilot.audit.AuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleService {
    private final JdbcClient jdbc; private final AuditService audit;
    public ScheduleService(JdbcClient jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
    public List<Schedule> list(){return jdbc.sql("SELECT id,week_start,schedule_status,copied_from_schedule_id,published_at FROM work_schedules ORDER BY week_start DESC LIMIT 10").query(Schedule.class).list();}
    public List<Assignment> assignments(long scheduleId){return jdbc.sql("SELECT a.id,a.shift_date,a.shift_code,a.starts_at,a.ends_at,u.dingtalk_user_id employee_id,u.name employee_name,a.is_dispatcher dispatcher FROM shift_assignments a JOIN users u ON u.id=a.user_id WHERE a.work_schedule_id=:id ORDER BY a.shift_date,a.starts_at,u.name").param("id",scheduleId).query(Assignment.class).list();}
    public CurrentSchedule current(LocalDate today){
        List<Schedule> schedules=jdbc.sql("SELECT id,week_start,schedule_status,copied_from_schedule_id,published_at FROM work_schedules WHERE week_start<=:today AND week_start+6>=:today AND schedule_status='published' ORDER BY id DESC LIMIT 1").param("today",today).query(Schedule.class).list();
        if(schedules.isEmpty())return new CurrentSchedule(null,List.of());
        Schedule schedule=schedules.getFirst();
        List<Assignment> rows=jdbc.sql("SELECT a.id,a.shift_date,a.shift_code,a.starts_at,a.ends_at,COALESCE(su.dingtalk_user_id,u.dingtalk_user_id) employee_id,COALESCE(su.name,u.name) employee_name,a.is_dispatcher dispatcher FROM shift_assignments a JOIN users u ON u.id=a.user_id LEFT JOIN LATERAL (SELECT substitute_user_id FROM shift_substitutions WHERE shift_assignment_id=a.id AND ended_at IS NULL AND starts_at<=now() AND ends_at>now() ORDER BY created_at DESC LIMIT 1) sub ON true LEFT JOIN users su ON su.id=sub.substitute_user_id WHERE a.work_schedule_id=:id ORDER BY a.shift_date,a.starts_at,employee_name")
                .param("id",schedule.id()).query(Assignment.class).list();
        return new CurrentSchedule(schedule,rows);
    }
    @Transactional public long create(String employee,long actor,LocalDate weekStart){
        if(weekStart.getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException("周排班必须从星期一开始");
        long id=jdbc.sql("INSERT INTO work_schedules(week_start,created_by,updated_by) VALUES(:week,:actor,:actor) ON CONFLICT(week_start) DO UPDATE SET updated_at=now(),updated_by=:actor RETURNING id").param("week",weekStart).param("actor",actor).query(Long.class).single();
        audit.record(employee,"schedules","create","work_schedule",String.valueOf(id));return id;
    }
    @Transactional public long copy(String employee,long actor,long source,LocalDate targetWeek){
        long target=create(employee,actor,targetWeek);
        LocalDate sourceWeek=jdbc.sql("SELECT week_start FROM work_schedules WHERE id=:id").param("id",source).query(LocalDate.class).single();
        long days=java.time.temporal.ChronoUnit.DAYS.between(sourceWeek,targetWeek);
        jdbc.sql("INSERT INTO shift_assignments(work_schedule_id,shift_date,shift_code,starts_at,ends_at,user_id,is_dispatcher) SELECT :target,shift_date+:days,shift_code,starts_at,ends_at,user_id,is_dispatcher FROM shift_assignments WHERE work_schedule_id=:source ON CONFLICT DO NOTHING")
                .param("target",target).param("days",(int)days).param("source",source).update();
        jdbc.sql("UPDATE work_schedules SET copied_from_schedule_id=:source,revision=revision+1 WHERE id=:target").param("source",source).param("target",target).update();
        audit.record(employee,"schedules","copy","work_schedule",String.valueOf(target),Map.of("source",source));return target;
    }
    @Transactional public Schedule publish(String employee,long actor,long scheduleId){
        int changed=jdbc.sql("UPDATE work_schedules SET schedule_status='published',published_at=now(),published_by=:actor,updated_by=:actor,updated_at=now(),revision=revision+1 WHERE id=:id AND schedule_status<>'archived'")
                .param("actor",actor).param("id",scheduleId).update();
        if(changed==0)throw new java.util.NoSuchElementException("排班不存在或已归档");
        audit.record(employee,"schedules","publish","work_schedule",String.valueOf(scheduleId));
        return jdbc.sql("SELECT id,week_start,schedule_status,copied_from_schedule_id,published_at FROM work_schedules WHERE id=:id").param("id",scheduleId).query(Schedule.class).single();
    }
    @Transactional public long assign(String employee,long actor,long schedule,AssignmentCommand command){
        long user=actorsUser(command.employeeId());
        long id=jdbc.sql("INSERT INTO shift_assignments(work_schedule_id,shift_date,shift_code,starts_at,ends_at,user_id,is_dispatcher) VALUES(:schedule,:date,:code,:starts,:ends,:user,:dispatcher) ON CONFLICT(work_schedule_id,shift_date,shift_code,user_id) DO UPDATE SET starts_at=:starts,ends_at=:ends,is_dispatcher=:dispatcher RETURNING id")
                .param("schedule",schedule).param("date",command.date()).param("code",command.shiftCode()).param("starts",command.startsAt()).param("ends",command.endsAt()).param("user",user).param("dispatcher",command.dispatcher()).query(Long.class).single();
        audit.record(employee,"schedules","assign","shift_assignment",String.valueOf(id));return id;
    }
    @Transactional public long substitute(String employee,long actor,long assignment,SubstitutionCommand command){
        long id=jdbc.sql("INSERT INTO shift_substitutions(shift_assignment_id,substitute_user_id,starts_at,ends_at,reason,created_by) VALUES(:assignment,:user,:starts,:ends,:reason,:actor) RETURNING id")
                .param("assignment",assignment).param("user",actorsUser(command.employeeId()))
                .param("starts",java.sql.Timestamp.from(command.startsAt())).param("ends",java.sql.Timestamp.from(command.endsAt()))
                .param("reason",command.reason()).param("actor",actor).query(Long.class).single();
        audit.record(employee,"schedules","substitute","shift_substitution",String.valueOf(id));return id;
    }
    private long actorsUser(String employee){return jdbc.sql("SELECT id FROM users WHERE dingtalk_user_id=:employee").param("employee",employee).query(Long.class).single();}
    public record Schedule(long id,LocalDate weekStart,String scheduleStatus,Long copiedFromScheduleId,Instant publishedAt){}
    public record Assignment(long id,LocalDate shiftDate,String shiftCode,LocalTime startsAt,LocalTime endsAt,String employeeId,String employeeName,boolean dispatcher){}
    public record AssignmentCommand(LocalDate date,String shiftCode,LocalTime startsAt,LocalTime endsAt,String employeeId,boolean dispatcher){}
    public record SubstitutionCommand(String employeeId,Instant startsAt,Instant endsAt,String reason){}
    public record CurrentSchedule(Schedule schedule,List<Assignment> assignments){}
}
