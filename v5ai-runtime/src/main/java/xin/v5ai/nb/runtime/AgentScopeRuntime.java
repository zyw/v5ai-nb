package xin.v5ai.nb.runtime;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.application.PublishedApplicationResolver;

public class AgentScopeRuntime implements AgentRuntime {
    private final PublishedApplicationResolver applicationResolver;
    private final AgentTextExecutor agentTextExecutor;

    public AgentScopeRuntime(PublishedApplicationResolver applicationResolver, AgentTextExecutor agentTextExecutor) {
        this.applicationResolver = applicationResolver;
        this.agentTextExecutor = agentTextExecutor;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        return Flux.defer(() -> {
            var application = applicationResolver.resolve(request.appKey());
            var runId = UUID.randomUUID().toString();
            var deltas = agentTextExecutor.streamText(application, request)
                    .map(delta -> RuntimeEvent.textDelta(runId, delta));
            return Flux.concat(Flux.just(RuntimeEvent.started(runId)), deltas, Flux.just(RuntimeEvent.completed(runId)));
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
