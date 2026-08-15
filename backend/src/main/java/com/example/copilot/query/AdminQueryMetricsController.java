package com.example.copilot.query;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/query-metrics")
public class AdminQueryMetricsController {
    private final QueryEventRecorder recorder;
    public AdminQueryMetricsController(QueryEventRecorder recorder) { this.recorder = recorder; }
    @GetMapping public Map<String, Object> metrics() { return recorder.metrics(); }
}
