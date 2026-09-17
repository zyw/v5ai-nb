package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.model.Model;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.RagSearchProvider;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;

import java.nio.file.Path;

/**
 * MCP AgentDTO 的文本执行器，负责解析 MCP AgentDTO 的模型定义，并使用 Stream 模式执行。
 */

public class PublishedModelAgentTextExecutor implements AgentTextExecutor {
    private final AgentModelResolver modelResolver;
    private final AgentScopeExecutorFactory executorFactory;

    /**
     * 默认构造：使用 Stream 模式执行。
     */
    public PublishedModelAgentTextExecutor(AgentModelResolver modelResolver) {
        this(modelResolver, ModelStreamTextExecutor::new);
    }


    /**
     * MCP/Skill 感知构造：解析出的每个模型执行器都会携带 MCP 解析器、审计仓储、
     * Skill 解析器与运行空间。
     */
    public PublishedModelAgentTextExecutor(
            AgentModelResolver modelResolver,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditRepository,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace
    ) {
        this(modelResolver, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace, null);
    }

    /**
     * MCP/Skill/联网搜索感知构造：额外携带 Tavily API Key，用于 AgentDTO 开启
     * {@code webSearchEnabled} 时注册联网搜索工具。
     */
    public PublishedModelAgentTextExecutor(
            AgentModelResolver modelResolver,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditRepository,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey
    ) {
        this(modelResolver, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace, webSearchApiKey, null);
    }

    /**
     * MCP/Skill/联网搜索/RAG 智能调用感知构造：额外携带 {@link RagSearchProvider}，
     * 供 AgentDTO 开启 RAG 智能调用模式时注册 {@code rag_search} 工具。
     */
    public PublishedModelAgentTextExecutor(
            AgentModelResolver modelResolver,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditRepository,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey,
            RagSearchProvider ragSearchProvider
    ) {
        this(modelResolver, model -> new ModelStreamTextExecutor(
                model, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace,
                webSearchApiKey, ragSearchProvider));
    }

    /**
     * 使用指定的执行器工厂构造 MCP AgentDTO 的文本执行器。
     */
    PublishedModelAgentTextExecutor(AgentModelResolver modelResolver, AgentScopeExecutorFactory executorFactory) {
        this.modelResolver = modelResolver;
        this.executorFactory = executorFactory;
    }

    /**
     * 使用指定的执行器工厂构造 MCP AgentDTO 的文本执行器。
     */
    @Override
    public Flux<AgentTextEvent> streamText(AgentDTO agent, AgentRunBo request) {
        var model = modelResolver.resolve(agent.modelId());
        if (model == null) {
            return Flux.error(new IllegalArgumentException("model is not resolvable: " + agent.modelId()));
        }
        return executorFactory.create(model).streamText(agent, request);
    }

    /**
     * MCP/Skill 感知的执行器工厂接口，用于创建 MCP/Skill 感知的执行器。
     */
    @FunctionalInterface
    interface AgentScopeExecutorFactory {
        AgentTextExecutor create(Model model);
    }
}
