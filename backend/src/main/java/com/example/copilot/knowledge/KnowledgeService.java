package com.example.copilot.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface KnowledgeService {
    Page list(String query, String status, int page, int size);
    Detail detail(String sourceKey);
    Detail save(String actor, SaveCommand command);
    Detail rollback(String actor, String sourceKey, int version, String reason);
    Detail disable(String actor, String sourceKey, String reason);

    record Summary(String sourceKey, String standardQuestion, String category, String status,
                   int currentVersion, boolean embedded, Instant validFrom, Instant validTo) {}
    record Page(List<Summary> items, long total, int page, int size) {}
    record Version(int version, String standardQuestion, List<String> userQuestions, List<String> keywords,
                   String originalAnswer, Instant validFrom, Instant validTo, String changeReason,
                   Instant createdAt) {}
    record Detail(Summary knowledge, List<Version> versions) {}
    record SaveCommand(String sourceKey, String category, String standardQuestion,
                       List<String> userQuestions, List<String> keywords, String originalAnswer,
                       Instant validFrom, Instant validTo, String reason) {}
    record Diff(int fromVersion, int toVersion, Map<String, Object> before, Map<String, Object> after) {}
}
