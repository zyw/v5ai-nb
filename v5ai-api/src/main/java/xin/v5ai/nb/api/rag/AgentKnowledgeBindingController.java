package xin.v5ai.nb.api.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.rag.KnowledgeManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/agents/{agentKey}/knowledge-bindings")
public class AgentKnowledgeBindingController {
    private final KnowledgeManagementService knowledgeManagementService;

    public AgentKnowledgeBindingController(KnowledgeManagementService knowledgeManagementService) {
        this.knowledgeManagementService = knowledgeManagementService;
    }

    @PostMapping
    public R<Void> bindKnowledgeBases(@PathVariable("agentKey") String agentKey,
                                   @RequestBody KnowledgeBindRequest request) {
        knowledgeManagementService.bindKnowledgeBases(agentKey, request.knowledgeBaseIds());
        return R.ok();
    }

    @GetMapping
    public R<List<Long>> getKnowledgeBindings(@PathVariable("agentKey") String agentKey) {
        return R.ok(knowledgeManagementService.getKnowledgeBindings(agentKey));
    }
}
