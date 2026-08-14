package xin.v5ai.nb.runtime;

import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;

@FunctionalInterface
public interface AgentTextExecutor {
    Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request);
}
