package xin.v5ai.nb.rag.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;
import xin.v5ai.nb.common.agentscope.core.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.rag.RagHit;

@Component
@RequiredArgsConstructor
public class DBRagContextProvider implements RagContextProvider {

    private final RetrievalContextBuilder retrievalContextBuilder;

    @Override
    public Mono<RagContext> buildContext(Agent agent, AgentRunRequest request) {
        return Mono.fromCallable(() -> {
            var result = retrievalContextBuilder.build(agent.agentKey(), request.query());
            var hits = result.citations().stream()
                    .map(citation -> new RagHit(citation.knowledgeBaseId(), citation.documentId(),
                            citation.documentTitle(), citation.chunkIndex(), citation.content(), citation.score()))
                    .toList();
            return new RagContext(result.context(), hits);
        });
    }
}
