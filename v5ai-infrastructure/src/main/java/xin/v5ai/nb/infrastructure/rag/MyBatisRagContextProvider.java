package xin.v5ai.nb.infrastructure.rag;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;
import xin.v5ai.nb.rag.RetrievalContextBuilder;

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
