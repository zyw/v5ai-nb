package xin.v5ai.nb.rag.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;

@Component
@RequiredArgsConstructor
public class DBRagContextProvider implements RagContextProvider {

    private final RetrievalContextBuilder retrievalContextBuilder;

    @Override
    public Mono<String> buildContext(Agent agent, AgentRunRequest request) {
        return Mono.fromCallable(() -> retrievalContextBuilder.build(agent.agentKey(), request.query()).context());
    }
}
