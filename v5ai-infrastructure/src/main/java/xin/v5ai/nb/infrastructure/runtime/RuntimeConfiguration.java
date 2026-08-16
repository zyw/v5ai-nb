package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.PublishedAgentResolver;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.runtime.AgentModelResolver;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.AgentScopeHarnessExecutor;
import xin.v5ai.nb.runtime.AgentScopeRuntime;
import xin.v5ai.nb.runtime.AgentStateRepository;
import xin.v5ai.nb.runtime.AgentTextEvent;
import xin.v5ai.nb.runtime.AgentTextExecutor;
import xin.v5ai.nb.runtime.ConversationRepository;
import xin.v5ai.nb.runtime.McpToolResolver;
import xin.v5ai.nb.runtime.MessageRepository;
import xin.v5ai.nb.runtime.PersistingAgentRuntime;
import xin.v5ai.nb.runtime.RagContextProvider;
import xin.v5ai.nb.runtime.RunEventRepository;
import xin.v5ai.nb.runtime.RunRecordRepository;
import xin.v5ai.nb.skill.SkillWorkspaceResolver;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(AgentScopeRuntimeProperties.class)
public class RuntimeConfiguration {
    @Bean
    AgentTextExecutor agentTextExecutor(
            ObjectProvider<AgentModelResolver> modelResolverProvider,
            ObjectProvider<Model> modelProvider,
            AgentScopeRuntimeProperties properties,
            ObjectProvider<McpToolResolver> mcpToolResolverProvider,
            ObjectProvider<McpToolCallAuditRepository> auditRepositoryProvider,
            ObjectProvider<SkillWorkspaceResolver> skillWorkspaceResolverProvider
    ) {
        McpToolResolver mcpToolResolver = mcpToolResolverProvider.getIfAvailable();
        McpToolCallAuditRepository auditRepository = auditRepositoryProvider.getIfAvailable();
        SkillWorkspaceResolver skillWorkspaceResolver = skillWorkspaceResolverProvider.getIfAvailable();
        AgentModelResolver modelResolver = modelResolverProvider.getIfAvailable();
        if (modelResolver != null) {
            return new PublishedModelAgentTextExecutor(
                    modelResolver,
                    mcpToolResolver,
                    auditRepository,
                    skillWorkspaceResolver,
                    Path.of(properties.getWorkspace()));
        }
        Model model = modelProvider.getIfAvailable();
        if (model != null) {
            return new AgentScopeHarnessExecutor(
                    model,
                    new JsonFileAgentStateStore(Path.of(properties.getStateDirectory())),
                    Path.of(properties.getWorkspace()));
        }
        return (Agent application, xin.v5ai.nb.runtime.AgentRunRequest request) ->
                Flux.just(new AgentTextEvent.Text("Phase 1 runtime placeholder response for: " + request.query()));
    }

    @Bean
    AgentRuntime agentRuntime(
            PublishedAgentResolver resolver,
            AgentTextExecutor executor,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository,
            ConversationRepository conversationRepository,
            RunEventRepository runEventRepository,
            ObjectProvider<AgentStateRepository> agentStateRepositoryProvider,
            ObjectProvider<RagContextProvider> ragContextProviderProvider,
            ObjectProvider<xin.v5ai.nb.common.platform.UsageRecorder> usageRecorderProvider,
            ObjectProvider<xin.v5ai.nb.common.platform.AppQuotaService> quotaServiceProvider
    ) {
        var runtime = new AgentScopeRuntime(resolver, executor, ragContextProviderProvider.getIfAvailable());
        return new PersistingAgentRuntime(runtime, messageRepository, runRecordRepository,
                conversationRepository, runEventRepository, agentStateRepositoryProvider.getIfAvailable(),
                usageRecorderProvider.getIfAvailable(), quotaServiceProvider.getIfAvailable());
    }
}
