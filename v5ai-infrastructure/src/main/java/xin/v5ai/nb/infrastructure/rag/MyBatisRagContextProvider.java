package xin.v5ai.nb.infrastructure.rag;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.rag.RetrievalContextBuilder;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.RagContextProvider;

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
