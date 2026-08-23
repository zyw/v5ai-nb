package xin.v5ai.nb.runtime.core;

import io.agentscope.core.model.Model;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.*;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.executor.ModelStreamTextExecutor;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;

import java.nio.file.Path;

public class PublishedModelAgentTextExecutor implements AgentTextExecutor {
    private final AgentModelResolver modelResolver;
    private final AgentScopeExecutorFactory executorFactory;

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
        this(modelResolver, model -> new ModelStreamTextExecutor(
                model, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace));
    }

    PublishedModelAgentTextExecutor(AgentModelResolver modelResolver, AgentScopeExecutorFactory executorFactory) {
        this.modelResolver = modelResolver;
        this.executorFactory = executorFactory;
    }

    @Override
    public Flux<AgentTextEvent> streamText(Agent application, AgentRunRequest request) {
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
