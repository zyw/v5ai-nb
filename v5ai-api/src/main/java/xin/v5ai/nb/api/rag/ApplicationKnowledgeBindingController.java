package xin.v5ai.nb.api.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.rag.KnowledgeManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/applications/{appKey}/knowledge-bindings")
public class ApplicationKnowledgeBindingController {
    private final KnowledgeManagementService knowledgeManagementService;

    public ApplicationKnowledgeBindingController(KnowledgeManagementService knowledgeManagementService) {
        this.knowledgeManagementService = knowledgeManagementService;
    }

    @PostMapping
    public void bindKnowledgeBases(@PathVariable("appKey") String appKey,
                                   @RequestBody KnowledgeBindRequest request) {
        knowledgeManagementService.bindKnowledgeBases(appKey, request.knowledgeBaseIds());
    }

    @GetMapping
    public List<Long> getKnowledgeBindings(@PathVariable("appKey") String appKey) {
        return knowledgeManagementService.getKnowledgeBindings(appKey);
    }
}
