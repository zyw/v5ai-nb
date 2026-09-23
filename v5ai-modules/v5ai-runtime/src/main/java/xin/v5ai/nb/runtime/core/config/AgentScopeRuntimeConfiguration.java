package xin.v5ai.nb.runtime.core.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.RagSearchProvider;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.executor.AgentScopeHarnessExecutor;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.executor.PublishedModelAgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.resolver.*;
import xin.v5ai.nb.common.agentscope.core.service.AttachmentContentProvider;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.model.api.ModelUsageService;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.runtime.core.AgentScopeRuntime;
import xin.v5ai.nb.runtime.core.ConversationSummaryGenerator;
import xin.v5ai.nb.runtime.core.ConversationTitleGenerator;
import xin.v5ai.nb.runtime.core.PersistingAgentRuntime;
import xin.v5ai.nb.runtime.core.RunCancellationRegistry;
import xin.v5ai.nb.runtime.core.config.properties.AgentScopeRuntimeProperties;
import xin.v5ai.nb.runtime.core.config.properties.HistoryWindowProperties;
import xin.v5ai.nb.runtime.core.config.properties.ChatAttachmentProperties;
import xin.v5ai.nb.runtime.core.service.*;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties({AgentScopeRuntimeProperties.class, HistoryWindowProperties.class})
public class AgentScopeRuntimeConfiguration {
    @Bean
    AgentTextExecutor agentTextExecutor(
            ObjectProvider<AgentModelResolver> modelResolverProvider,
            ObjectProvider<Model> modelProvider,
            AgentScopeRuntimeProperties properties,
            ObjectProvider<McpToolResolver> mcpToolResolverProvider,
            ObjectProvider<McpToolCallAuditService> auditRepositoryProvider,
            ObjectProvider<SkillWorkspaceResolver> skillWorkspaceResolverProvider,
            ObjectProvider<RagSearchProvider> ragSearchProviderProvider,
            ObjectProvider<AttachmentContentProvider> attachmentContentProviderProvider,
            ChatAttachmentProperties attachmentProperties
    ) {
        McpToolResolver mcpToolResolver = mcpToolResolverProvider.getIfAvailable();
        McpToolCallAuditService auditRepository = auditRepositoryProvider.getIfAvailable();
        SkillWorkspaceResolver skillWorkspaceResolver = skillWorkspaceResolverProvider.getIfAvailable();
        RagSearchProvider ragSearchProvider = ragSearchProviderProvider.getIfAvailable();
        AgentModelResolver modelResolver = modelResolverProvider.getIfAvailable();
        if (modelResolver != null) {
            return new PublishedModelAgentTextExecutor(
                    modelResolver,
                    mcpToolResolver,
                    auditRepository,
                    skillWorkspaceResolver,
                    Path.of(properties.getWorkspace()),
                    properties.getTavilyApiKey(),
                    ragSearchProvider,
                    attachmentContentProviderProvider.getIfAvailable(),
                    attachmentProperties.getHistoryBudgetBytes(),
                    properties.getImageTokensPerImage());
        }
        Model model = modelProvider.getIfAvailable();
        if (model != null) {
            return new AgentScopeHarnessExecutor(
                    model,
                    new JsonFileAgentStateStore(Path.of(properties.getStateDirectory())),
                    Path.of(properties.getWorkspace()));
        }
        return (AgentDTO application, AgentRunBo request) ->
                Flux.just(new AgentTextEvent.Text("Phase 1 runtime placeholder response for: " + request.query()));
    }

    @Bean
    AgentRuntime agentRuntime(
            PublishedAgentResolver resolver,
            AgentTextExecutor executor,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            RunCancellationRegistry cancellationRegistry,
            ObjectProvider<AgentStateService> agentStateRepositoryProvider,
            ObjectProvider<RagContextProvider> ragContextProviderProvider,
            ObjectProvider<ModelToolSupportResolver> modelToolSupportResolverProvider,
            ObjectProvider<ModelUsageService> usageRecorderProvider,
            ObjectProvider<AppQuotaService> quotaServiceProvider,
            ConversationTitleGenerator conversationTitleGenerator,
            ConversationSummaryGenerator conversationSummaryGenerator,
            ConversationSummaryService conversationSummaryService,
            HistoryWindowProperties historyWindowProperties,
            AgentScopeRuntimeProperties properties
    ) {
        var runtime = new AgentScopeRuntime(resolver::resolve, executor, ragContextProviderProvider.getIfAvailable(),
                messageService, modelToolSupportResolverProvider.getIfAvailable(),
                historyWindowProperties.toWindow(), conversationSummaryService);
        return new PersistingAgentRuntime(runtime, messageService, runRecordService,
                conversationService, runEventService, agentStateRepositoryProvider.getIfAvailable(),
                usageRecorderProvider.getIfAvailable(), quotaServiceProvider.getIfAvailable(),
                cancellationRegistry, conversationTitleGenerator, conversationSummaryGenerator,
                properties.isReasoningPersistEnabled());
    }

    /**
     * 管理端调试运行时：允许草稿态 AgentDTO 运行（{@code resolveForDebug}），
     * 供后台「预览与调试」面板在未发布时直接调试；仅禁用状态拒绝。
     */
    @Bean
    AgentRuntime agentDebugRuntime(
            PublishedAgentResolver resolver,
            AgentTextExecutor executor,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            RunCancellationRegistry cancellationRegistry,
            ObjectProvider<AgentStateService> agentStateRepositoryProvider,
            ObjectProvider<RagContextProvider> ragContextProviderProvider,
            ObjectProvider<ModelToolSupportResolver> modelToolSupportResolverProvider,
            ObjectProvider<ModelUsageService> usageRecorderProvider,
            ObjectProvider<AppQuotaService> quotaServiceProvider,
            ConversationTitleGenerator conversationTitleGenerator,
            ConversationSummaryGenerator conversationSummaryGenerator,
            ConversationSummaryService conversationSummaryService,
            HistoryWindowProperties historyWindowProperties,
            AgentScopeRuntimeProperties properties
    ) {
        var runtime = new AgentScopeRuntime(resolver::resolveForDebug, executor, ragContextProviderProvider.getIfAvailable(),
                messageService, modelToolSupportResolverProvider.getIfAvailable(),
                historyWindowProperties.toWindow(), conversationSummaryService);
        return new PersistingAgentRuntime(runtime, messageService, runRecordService,
                conversationService, runEventService, agentStateRepositoryProvider.getIfAvailable(),
                usageRecorderProvider.getIfAvailable(), quotaServiceProvider.getIfAvailable(),
                cancellationRegistry, conversationTitleGenerator, conversationSummaryGenerator,
                properties.isReasoningPersistEnabled());
    }
}
