package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.PublishedApplicationResolver;
import xin.v5ai.nb.runtime.AgentScopeHarnessExecutor;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.AgentScopeRuntime;
import xin.v5ai.nb.runtime.AgentModelResolver;
import xin.v5ai.nb.runtime.AgentTextExecutor;
import xin.v5ai.nb.runtime.ConversationRepository;
import xin.v5ai.nb.runtime.MessageRepository;
import xin.v5ai.nb.runtime.PersistingAgentRuntime;
import xin.v5ai.nb.runtime.RunEventRepository;
import xin.v5ai.nb.runtime.RunRecordRepository;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(AgentScopeRuntimeProperties.class)
public class RuntimeConfiguration {
    @Bean
    AgentTextExecutor agentTextExecutor(
            ObjectProvider<AgentModelResolver> modelResolverProvider,
            ObjectProvider<Model> modelProvider,
            AgentScopeRuntimeProperties properties
    ) {
        AgentModelResolver modelResolver = modelResolverProvider.getIfAvailable();
        if (modelResolver != null) {
            return new PublishedModelAgentTextExecutor(modelResolver);
        }
        Model model = modelProvider.getIfAvailable();
        if (model != null) {
            return new AgentScopeHarnessExecutor(
                    model,
                    new JsonFileAgentStateStore(Path.of(properties.getStateDirectory())),
                    Path.of(properties.getWorkspace()));
        }
        return (Application application, xin.v5ai.nb.runtime.AgentRunRequest request) ->
                Flux.just("Phase 1 runtime placeholder response for: ", request.query());
    }

    @Bean
    AgentRuntime agentRuntime(
            PublishedApplicationResolver resolver,
            AgentTextExecutor executor,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository,
            ConversationRepository conversationRepository,
            RunEventRepository runEventRepository
    ) {
        var runtime = new AgentScopeRuntime(resolver, executor);
        return new PersistingAgentRuntime(runtime, messageRepository, runRecordRepository,
                conversationRepository, runEventRepository);
    }
}
