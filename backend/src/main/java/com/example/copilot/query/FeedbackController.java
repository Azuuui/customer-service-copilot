package com.example.copilot.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    private final FeedbackService service;
    public FeedbackController(FeedbackService service) { this.service = service; }

    @PostMapping
    public FeedbackService.Result report(@Valid @RequestBody Request request,
                                         @RequestHeader(value="X-Anonymous-Session", required=false) String session) {
        String key = session == null || session.isBlank() ? UUID.randomUUID().toString() : session;
        return service.report(key, request.query().trim(), request.type(), request.detail(), request.confirmDuplicate());
    }

    public record Request(@NotBlank String query,
                          @Pattern(regexp="answer_issue|no_match|outdated|incomplete|unclear") String type,
                          String detail, boolean confirmDuplicate) {
        public Request { if (type == null || type.isBlank()) type = "answer_issue"; }
    }
}
