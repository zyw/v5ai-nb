package xin.v5ai.nb.rag.core;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;

@Component
public class MyBatisRagContextProvider implements RagContextProvider {
    private final RetrievalContextBuilder retrievalContextBuilder;

    public MyBatisRagContextProvider(RetrievalContextBuilder retrievalContextBuilder) {
        this.retrievalContextBuilder = retrievalContextBuilder;
    }

    @Override
    public Mono<String> buildContext(Agent application, AgentRunRequest request) {
        return Mono.fromCallable(() -> retrievalContextBuilder.build(application.agentKey(), request.query()).context());
    }
}
