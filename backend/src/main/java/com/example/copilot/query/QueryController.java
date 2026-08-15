package com.example.copilot.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class QueryController {
    private final RetrievalClient retrieval;
    private final QueryEventRecorder events;

    public QueryController(RetrievalClient retrieval, QueryEventRecorder events) {
        this.retrieval = retrieval;
        this.events = events;
    }
    @Value("${copilot.persistence:memory}")
    private String persistence;
    @Value("${copilot.auth.mode:disabled}")
    private String authMode;
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> invalidRequest() { return Map.of("error", "query_required"); }

    @GetMapping("/health")
    public Map<String, Object> health() {
        JsonNode retrievalHealth = retrieval.health();
        return Map.of("status", "ok", "version", "0.2.0",
                "persistence", persistence, "authMode", authMode,
                "retrieval", retrievalHealth == null ? Map.of("status", "unavailable") : retrievalHealth);
    }

    @PostMapping("/query")
    public JsonNode query(@Valid @RequestBody QueryRequest request,
                          @RequestHeader(value = "X-Anonymous-Session", required = false) String anonymousSession) {
        if (request.query().isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "query_required");
        long started = System.nanoTime();
        JsonNode response = retrieval.search(request.query().trim(), request.limit(), request.offset());
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        String sessionKey = anonymousSession == null || anonymousSession.isBlank() ? UUID.randomUUID().toString() : anonymousSession;
        events.record(sessionKey, request.query().trim(), request.requestKind(), response.path("results").size(), latencyMs, UUID.randomUUID().toString());
        return response;
    }

    public record QueryRequest(@NotBlank String query, @Min(1) @Max(4) int limit, @Min(0) int offset,
                               @Pattern(regexp = "query|display_more|refresh|automatic_retry") String requestKind) {
        public QueryRequest {
            if (limit == 0) limit = 4;
            if (requestKind == null || requestKind.isBlank()) requestKind = "query";
        }
    }
}
