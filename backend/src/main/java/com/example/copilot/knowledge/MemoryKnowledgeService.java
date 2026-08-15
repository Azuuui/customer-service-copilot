package com.example.copilot.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name="copilot.persistence", havingValue="memory", matchIfMissing=true)
public class MemoryKnowledgeService implements KnowledgeService {
    private final Map<String, Detail> data = new ConcurrentHashMap<>();

    @Override public Page list(String query, String status, int page, int size) {
        List<Summary> rows = data.values().stream().map(Detail::knowledge)
                .filter(item -> query == null || query.isBlank() || item.standardQuestion().contains(query))
                .filter(item -> status == null || status.isBlank() || item.status().equals(status)).toList();
        int from = Math.min(page * size, rows.size());
        return new Page(rows.subList(from, Math.min(from + size, rows.size())), rows.size(), page, size);
    }
    @Override public Detail detail(String sourceKey) { return required(sourceKey); }
    @Override public Detail save(String actor, SaveCommand command) {
        String key = command.sourceKey() == null || command.sourceKey().isBlank() ? UUID.randomUUID().toString() : command.sourceKey();
        Detail old = data.get(key); int version = old == null ? 1 : old.versions().size() + 1;
        Version next = new Version(version, command.standardQuestion(), safe(command.userQuestions()), safe(command.keywords()),
                command.originalAnswer(), command.validFrom(), command.validTo(), command.reason(), Instant.now());
        List<Version> versions = new ArrayList<>(old == null ? List.of() : old.versions()); versions.add(next);
        Summary summary = new Summary(key, command.standardQuestion(), command.category(), "active", version, false, command.validFrom(), command.validTo());
        Detail result = new Detail(summary, List.copyOf(versions)); data.put(key, result); return result;
    }
    @Override public Detail rollback(String actor, String sourceKey, int version, String reason) {
        Version target = required(sourceKey).versions().stream().filter(item -> item.version()==version).findFirst().orElseThrow();
        Detail old = required(sourceKey);
        return save(actor, new SaveCommand(sourceKey, old.knowledge().category(), target.standardQuestion(), target.userQuestions(), target.keywords(), target.originalAnswer(), target.validFrom(), target.validTo(), reason));
    }
    @Override public Detail disable(String actor, String sourceKey, String reason) {
        Detail old=required(sourceKey); Summary item=old.knowledge();
        Detail next=new Detail(new Summary(item.sourceKey(),item.standardQuestion(),item.category(),"disabled",item.currentVersion(),item.embedded(),item.validFrom(),item.validTo()),old.versions()); data.put(sourceKey,next); return next;
    }
    private Detail required(String key) { return Optional.ofNullable(data.get(key)).orElseThrow(NoSuchElementException::new); }
    private List<String> safe(List<String> value) { return value == null ? List.of() : List.copyOf(value); }
}
