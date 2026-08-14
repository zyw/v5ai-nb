package xin.v5ai.nb.runtime;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.application.PublishedApplicationResolver;

public class AgentScopeRuntime implements AgentRuntime {
    private final PublishedApplicationResolver applicationResolver;
    private final AgentTextExecutor agentTextExecutor;
    private final RagContextProvider ragContextProvider;

    public AgentScopeRuntime(PublishedApplicationResolver applicationResolver, AgentTextExecutor agentTextExecutor) {
        this(applicationResolver, agentTextExecutor, null);
    }

    public AgentScopeRuntime(PublishedApplicationResolver applicationResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider) {
        this.applicationResolver = applicationResolver;
        this.agentTextExecutor = agentTextExecutor;
        this.ragContextProvider = ragContextProvider;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        return Flux.defer(() -> {
            var application = applicationResolver.resolve(request.appKey());
            var runId = UUID.randomUUID().toString();
            var contextMono = ragContextProvider == null
                    ? Mono.just("")
                    : ragContextProvider.buildContext(application, request);
            return Flux.concat(
                    Flux.just(RuntimeEvent.started(runId)),
                    contextMono.flatMapMany(context -> {
                        var enriched = (context == null || context.isBlank())
                                ? request
                                : request.withRagContext(context);
                        var events = agentTextExecutor.streamText(application, enriched)
                                .map(event -> switch (event) {
                                    case AgentTextEvent.Text text -> RuntimeEvent.textDelta(runId, text.text());
                                    case AgentTextEvent.ModelCall call -> RuntimeEvent.modelCall(runId, call.modelName());
                                });
                        if (context != null && !context.isBlank()) {
                            return Flux.concat(Flux.just(RuntimeEvent.retrieval(runId, context)), events);
                        }
                        return events;
                    }),
                    Flux.just(RuntimeEvent.completed(runId)));
        });
    }

    @Override
    public Mono<AgentRunResult> call(AgentRunRequest request) {
        return stream(request)
                .filter(event -> event.type() == RuntimeEventType.TEXT_DELTA)
                .map(RuntimeEvent::payload)
                .collectList()
                .map(parts -> new AgentRunResult(UUID.randomUUID().toString(), String.join("", parts)));
    }
}
