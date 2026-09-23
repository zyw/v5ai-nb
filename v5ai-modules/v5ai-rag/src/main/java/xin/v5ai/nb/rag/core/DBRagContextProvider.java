package xin.v5ai.nb.rag.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.RagSearchProvider;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.KnowledgeBaseRef;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.rag.core.dto.RetrievalResult;
import xin.v5ai.nb.rag.service.IAgentKnowledgeBindingService;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;

import java.util.List;

/**
 * RAG 运行时端口实现（强制调用的 {@link RagContextProvider} + 智能调用的 {@link RagSearchProvider}）。
 */
@Component
@RequiredArgsConstructor
public class DBRagContextProvider implements RagContextProvider, RagSearchProvider {

    private final RetrievalContextBuilder retrievalContextBuilder;
    private final IAgentKnowledgeBindingService bindingService;
    private final IKnowledgeBaseService baseService;

    @Override
    public Mono<RagContext> buildContext(AgentDTO agent, AgentRunBo request) {
        return Mono.fromCallable(() -> {
            var result = retrievalContextBuilder.build(agent.agentKey(), request.query());
            return toRagContext(result);
        });
    }

    @Override
    public List<KnowledgeBaseRef> listBoundKnowledgeBases(String agentKey) {
        return bindingService.findByAgentKey(agentKey).stream()
                .map(baseService::getKnowledgeBase)
                .filter(base -> base != null)
                .map(base -> new KnowledgeBaseRef(base.getId(), base.getName(), base.getDescription()))
                .toList();
    }

    @Override
    public RagContext search(String agentKey, long knowledgeBaseId, String query) {
        // 知识库未绑定到该 AgentDTO 时由 RetrievalContextBuilder.search 抛异常，工具侧转为友好错误
        return toRagContext(retrievalContextBuilder.search(agentKey, knowledgeBaseId, query));
    }

    private static RagContext toRagContext(RetrievalResult result) {
        var hits = result.citations().stream()
                .map(citation -> new RagHit(citation.knowledgeBaseId(), citation.documentId(),
                        citation.documentTitle(), citation.chunkIndex(), citation.content(), citation.score()))
                .toList();
        return new RagContext(result.context(), hits);
    }
}