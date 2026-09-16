package xin.v5ai.nb.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.rag.domain.bo.KnowledgeBindBo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.util.List;

/**
 * AgentDTO 绑定知识库 API。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agents/{agentKey}/knowledge-bindings")
public class AgentKnowledgeBindingController extends BaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public R<Void> bindKnowledgeBases(@PathVariable("agentKey") String agentKey,
                                      @RequestBody KnowledgeBindBo request) {
        if (request == null || request.knowledgeBaseIds() == null) {
            throw new IllegalArgumentException("knowledgeBaseIds is required");
        }
        knowledgeBaseService.bindKnowledgeBases(agentKey, request.knowledgeBaseIds());
        return R.ok();
    }

    @GetMapping
    public R<List<Long>> getKnowledgeBindings(@PathVariable("agentKey") String agentKey) {
        return R.ok(knowledgeBaseService.getKnowledgeBindings(agentKey));
    }
}
