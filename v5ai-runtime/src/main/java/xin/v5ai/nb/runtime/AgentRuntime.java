package xin.v5ai.nb.runtime;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentRuntime {
    Flux<RuntimeEvent> stream(AgentRunRequest request);

    Mono<AgentRunResult> call(AgentRunRequest request);
}
