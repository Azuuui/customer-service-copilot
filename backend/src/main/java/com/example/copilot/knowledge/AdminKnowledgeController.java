package com.example.copilot.knowledge;

import com.example.copilot.query.RetrievalClient;
import com.example.copilot.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class AdminKnowledgeController {
    private final KnowledgeService knowledge;
    private final RetrievalClient retrieval;
    public AdminKnowledgeController(KnowledgeService knowledge, RetrievalClient retrieval) { this.knowledge=knowledge; this.retrieval=retrieval; }

    @GetMapping public KnowledgeService.Page list(@RequestParam(defaultValue="") String query,
                                                   @RequestParam(defaultValue="") String status,
                                                   @RequestParam(defaultValue="0") @Min(0) int page,
                                                   @RequestParam(defaultValue="10") @Min(1) int size) {
        return knowledge.list(query,status,page,Math.min(size,100));
    }
    @GetMapping("/{sourceKey}") public KnowledgeService.Detail detail(@PathVariable String sourceKey) { return knowledge.detail(sourceKey); }
    @GetMapping("/{sourceKey}/diff") public KnowledgeService.Diff diff(@PathVariable String sourceKey,@RequestParam int from,@RequestParam int to){
        var versions=knowledge.detail(sourceKey).versions();
        var before=versions.stream().filter(v->v.version()==from).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        var after=versions.stream().filter(v->v.version()==to).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        return new KnowledgeService.Diff(from,to,versionMap(before),versionMap(after));
    }
    @PostMapping public Map<String,Object> create(@Valid @RequestBody SaveRequest request, HttpServletRequest http) {
        return save(null,request,http);
    }
    @PutMapping("/{sourceKey}") public Map<String,Object> update(@PathVariable String sourceKey, @Valid @RequestBody SaveRequest request, HttpServletRequest http) {
        return save(sourceKey,request,http);
    }
    @PostMapping("/{sourceKey}/rollback") public Map<String,Object> rollback(@PathVariable String sourceKey,
            @RequestBody RollbackRequest request,HttpServletRequest http) {
        KnowledgeService.Detail detail=knowledge.rollback(actor(http),sourceKey,request.version(),request.reason());
        return Map.of("knowledge",detail,"indexReady",retrieval.reindex(sourceKey));
    }
    @PostMapping("/{sourceKey}/disable") public KnowledgeService.Detail disable(@PathVariable String sourceKey,
            @RequestBody ReasonRequest request,HttpServletRequest http) {
        return knowledge.disable(actor(http),sourceKey,request.reason());
    }
    private Map<String,Object> save(String sourceKey,SaveRequest request,HttpServletRequest http) {
        KnowledgeService.Detail detail=knowledge.save(actor(http),new KnowledgeService.SaveCommand(sourceKey,request.category(),
                request.standardQuestion(),request.userQuestions(),request.keywords(),request.originalAnswer(),
                request.validFrom(),request.validTo(),request.reason()));
        return Map.of("knowledge",detail,"indexReady",retrieval.reindex(detail.knowledge().sourceKey()));
    }
    private String actor(HttpServletRequest request){return String.valueOf(request.getAttribute(SessionAuthenticationInterceptor.EMPLOYEE_ID));}
    private Map<String,Object> versionMap(KnowledgeService.Version value){return Map.of("standardQuestion",value.standardQuestion(),"userQuestions",value.userQuestions(),"keywords",value.keywords(),"originalAnswer",value.originalAnswer(),"changeReason",value.changeReason());}
    public record SaveRequest(@NotBlank String category,@NotBlank String standardQuestion,java.util.List<String> userQuestions,
                              java.util.List<String> keywords,@NotBlank String originalAnswer,java.time.Instant validFrom,
                              java.time.Instant validTo,@NotBlank String reason){}
    public record RollbackRequest(int version,@NotBlank String reason){}
    public record ReasonRequest(@NotBlank String reason){}
}
