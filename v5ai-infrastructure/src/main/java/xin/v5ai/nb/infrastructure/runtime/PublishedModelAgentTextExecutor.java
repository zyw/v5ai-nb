package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.model.Model;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.runtime.AgentModelResolver;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.AgentTextEvent;
import xin.v5ai.nb.runtime.AgentTextExecutor;
import xin.v5ai.nb.runtime.ModelStreamTextExecutor;

public class PublishedModelAgentTextExecutor implements AgentTextExecutor {
    private final AgentModelResolver modelResolver;
    private final AgentScopeExecutorFactory executorFactory;

    public PublishedModelAgentTextExecutor(AgentModelResolver modelResolver) {
        this(modelResolver, ModelStreamTextExecutor::new);
    }

    PublishedModelAgentTextExecutor(AgentModelResolver modelResolver, AgentScopeExecutorFactory executorFactory) {
        this.modelResolver = modelResolver;
        this.executorFactory = executorFactory;
    }

    @Override
    public Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request) {
        var model = modelResolver.resolve(application.modelId());
        if (model == null) {
            return Flux.error(new IllegalArgumentException("model is not resolvable: " + application.modelId()));
        }
        return executorFactory.create(model).streamText(application, request);
    }

    @FunctionalInterface
    interface AgentScopeExecutorFactory {
        AgentTextExecutor create(Model model);
    }
}
