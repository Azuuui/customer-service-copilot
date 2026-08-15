package com.example.copilot.operations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/schedules")
public class ScheduleController {
    private final ScheduleService service;private final OperationsActor actors;
    public ScheduleController(ScheduleService service,OperationsActor actors){this.service=service;this.actors=actors;}
    @GetMapping public List<ScheduleService.Schedule> list(){return service.list();}
    @GetMapping("/{id}/assignments") public List<ScheduleService.Assignment> assignments(@PathVariable long id){return service.assignments(id);}
    @PostMapping public Map<String,Long> create(@RequestBody Week body,HttpServletRequest request){String employee=actors.employee(request);return Map.of("id",service.create(employee,actors.userId(employee),body.weekStart()));}
    @PostMapping("/{id}/copy") public Map<String,Long> copy(@PathVariable long id,@RequestBody Week body,HttpServletRequest request){String employee=actors.employee(request);return Map.of("id",service.copy(employee,actors.userId(employee),id,body.weekStart()));}
    @PostMapping("/{id}/publish") public ScheduleService.Schedule publish(@PathVariable long id,HttpServletRequest request){String employee=actors.employee(request);return service.publish(employee,actors.userId(employee),id);}
    @PostMapping("/{id}/assignments") public Map<String,Long> assign(@PathVariable long id,@RequestBody ScheduleService.AssignmentCommand body,HttpServletRequest request){String employee=actors.employee(request);return Map.of("id",service.assign(employee,actors.userId(employee),id,body));}
    @PostMapping("/assignments/{id}/substitutions") public Map<String,Long> substitute(@PathVariable long id,@RequestBody ScheduleService.SubstitutionCommand body,HttpServletRequest request){String employee=actors.employee(request);return Map.of("id",service.substitute(employee,actors.userId(employee),id,body));}
    public record Week(LocalDate weekStart){}
}
