package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.PublishedApplicationResolver;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.AgentScopeRuntime;
import xin.v5ai.nb.runtime.AgentTextExecutor;
import xin.v5ai.nb.runtime.MessageRepository;
import xin.v5ai.nb.runtime.PersistingAgentRuntime;
import xin.v5ai.nb.runtime.RunRecordRepository;

@Configuration
public class RuntimeConfiguration {
    @Bean
    AgentTextExecutor agentTextExecutor() {
        return (Application application, xin.v5ai.nb.runtime.AgentRunRequest request) ->
                Flux.just("Phase 1 runtime placeholder response for: ", request.query());
    }

    @Bean
    AgentRuntime agentRuntime(
            PublishedApplicationResolver resolver,
            AgentTextExecutor executor,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository
    ) {
        var runtime = new AgentScopeRuntime(resolver, executor);
        return new PersistingAgentRuntime(runtime, messageRepository, runRecordRepository);
    }
}
