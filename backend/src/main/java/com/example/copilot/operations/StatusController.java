package com.example.copilot.operations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class StatusController {
    private final StatusService service;private final OperationsActor actors;
    public StatusController(StatusService service,OperationsActor actors){this.service=service;this.actors=actors;}
    @GetMapping("/status/rules") public List<StatusService.Rule> rules(){return service.rules();}
    @GetMapping("/status/dashboard") public StatusService.Dashboard dashboard(HttpServletRequest request){return service.dashboardFor(actors.employee(request));}
    @GetMapping("/admin/status/dashboard") public StatusService.Dashboard adminDashboard(){return service.dashboard();}
    @PostMapping("/status/requests") public Map<String,Long> request(@RequestBody StatusService.RequestCommand body,HttpServletRequest request){String employee=actors.employee(request);return Map.of("id",service.request(employee,actors.userId(employee),null,body,false));}
    @GetMapping("/admin/status/requests") public List<StatusService.RequestItem> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return service.history(page,Math.min(Math.max(size,1),100));}
    @PostMapping("/admin/status/arrange") public Map<String,Long> arrange(@RequestBody Arrange body,HttpServletRequest request){String employee=actors.employee(request);return Map.of("id",service.request(employee,actors.userId(employee),body.employeeId(),new StatusService.RequestCommand(body.statusCode(),body.durationMinutes()),true));}
    @PostMapping("/admin/status/requests/{id}/approve") public StatusService.RequestItem approve(@PathVariable long id,@RequestBody Decision body,HttpServletRequest request){String employee=actors.employee(request);return service.approve(employee,actors.userId(employee),id,body.allowOverCapacity(),body.reason());}
    @PostMapping("/admin/status/requests/{id}/reorder") public StatusService.RequestItem reorder(@PathVariable long id,@RequestBody Reorder body,HttpServletRequest request){String employee=actors.employee(request);return service.reorder(employee,actors.userId(employee),id,body.position(),body.reason());}
    @PostMapping("/admin/status/requests/{id}/end") public Map<String,String> end(@PathVariable long id,@RequestBody Reason body,HttpServletRequest request){String employee=actors.employee(request);service.end(employee,actors.userId(employee),id,body.reason());return Map.of("status","ended");}
    public record Arrange(String employeeId,String statusCode,Integer durationMinutes){}
    public record Decision(boolean allowOverCapacity,String reason){}
    public record Reorder(int position,String reason){}
    public record Reason(String reason){}
}
