package com.example.copilot.operations;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class WorkbenchScheduleController {
    private final ScheduleService schedules;
    public WorkbenchScheduleController(ScheduleService schedules){this.schedules=schedules;}
    @GetMapping("/api/v1/schedules/current") public ScheduleService.CurrentSchedule current(){return schedules.current(LocalDate.now());}
}
