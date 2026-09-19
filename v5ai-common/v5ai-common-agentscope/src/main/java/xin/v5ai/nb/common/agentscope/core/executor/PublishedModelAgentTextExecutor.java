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
import xin.v5ai.nb.common.agentscope.core.service.AttachmentContentProvider;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;

import java.nio.file.Path;

/**
 * 按 Agent 配置解析模型的文本执行器：用 {@link AgentModelResolver} 把 Agent 上的
 * {@code modelId} 解析为 {@link Model}，再交给 {@link ModelStreamTextExecutor} 流式执行。
 *
 * <p>与固定单一模型的 {@link AgentScopeHarnessExecutor} 相对，本类支持每个 Agent 使用各自的模型。
 * 它只做「解析模型 + 委派」，不直接装配能力：MCP / Skill / 联网搜索 / RAG 智能调用由被委派的
 * 执行器按 Agent 的能力开关决定。模型解析不到时返回 {@code Flux.error(IllegalArgumentException)}，
 * 不抛同步异常。</p>
 */
public class PublishedModelAgentTextExecutor implements AgentTextExecutor {

    private final AgentModelResolver modelResolver;
    private final AgentScopeExecutorFactory executorFactory;

    /**
     * 最简构造：委派 {@link ModelStreamTextExecutor} 流式执行，不携带 MCP / Skill /
     * 联网搜索 / RAG 依赖。
     */
    public PublishedModelAgentTextExecutor(AgentModelResolver modelResolver) {
        this(modelResolver, ModelStreamTextExecutor::new);
    }


    /**
     * MCP / Skill 感知构造：为每个解析出的模型创建携带 MCP 解析器、审计仓储、Skill 解析器
     * 与运行空间的 {@link ModelStreamTextExecutor}；联网搜索与 RAG 智能调用未启用（对应参数为 {@code null}）。
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
     * MCP / Skill / 联网搜索感知构造：在上一构造的基础上携带 Tavily API Key，
     * 供 Agent 开启 {@code webSearchEnabled} 时注册联网搜索工具；RAG 智能调用未启用。
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
     * MCP / Skill / 联网搜索 / RAG 智能调用感知构造：额外携带 {@link RagSearchProvider}，
     * 供 Agent 开启 RAG 智能调用模式时注册 {@code rag_search} 工具。
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
        this(modelResolver, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace,
                webSearchApiKey, ragSearchProvider, null, ModelStreamTextExecutor.DEFAULT_HISTORY_IMAGE_BUDGET_BYTES);
    }

    /**
     * 多模态感知构造：在上一构造的基础上装配附件内容读取端口与历史图片体积预算，
     * 供消息携带图片附件时真正把图片送进模型输入。
     *
     * @param attachmentContentProvider 附件读取端口；{@code null} 时忽略附件（纯文本）
     * @param historyImageBudgetBytes   历史回放中图片的累计体积预算（字节）
     */
    public PublishedModelAgentTextExecutor(
            AgentModelResolver modelResolver,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditRepository,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey,
            RagSearchProvider ragSearchProvider,
            AttachmentContentProvider attachmentContentProvider,
            long historyImageBudgetBytes
    ) {
        this(modelResolver, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace,
                webSearchApiKey, ragSearchProvider, attachmentContentProvider, historyImageBudgetBytes,
                ModelStreamTextExecutor.DEFAULT_IMAGE_TOKENS);
    }

    /**
     * 全参构造 + 用量估算参数：额外指定「每张图片折算多少 token」，供用量估算兜底使用。
     *
     * @param imageTokensPerImage 图片折算的 token 数（见 {@link ModelStreamTextExecutor#DEFAULT_IMAGE_TOKENS}）
     */
    public PublishedModelAgentTextExecutor(
            AgentModelResolver modelResolver,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditRepository,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey,
            RagSearchProvider ragSearchProvider,
            AttachmentContentProvider attachmentContentProvider,
            long historyImageBudgetBytes,
            long imageTokensPerImage
    ) {
        this(modelResolver, model -> new ModelStreamTextExecutor(
                model, mcpToolResolver, auditRepository, skillWorkspaceResolver, workspace,
                webSearchApiKey, ragSearchProvider, attachmentContentProvider, historyImageBudgetBytes,
                imageTokensPerImage));
    }

    /**
     * 装配/测试用构造：直接指定执行器工厂（包级可见），默认实现即
     * {@code ModelStreamTextExecutor::new}。
     *
     * @param modelResolver   模型解析器：按 Agent 的 {@code modelId} 解析出模型
     * @param executorFactory 由解析出的模型创建文本执行器的工厂
     */
    PublishedModelAgentTextExecutor(AgentModelResolver modelResolver, AgentScopeExecutorFactory executorFactory) {
        this.modelResolver = modelResolver;
        this.executorFactory = executorFactory;
    }

    /**
     * 解析 Agent 的模型并流式执行；模型不可解析时返回
     * {@code Flux.error(IllegalArgumentException)}，交由运行时的失败路径处理。
     *
     * @param agent   已解析的 Agent 配置（取其中的 {@code modelId}）
     * @param request 本次运行的请求（含 query 与可选 RAG 上下文）
     * @return 文本事件流（由被委派的执行器产出）
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
     * 执行器工厂：把已解析的 {@link Model} 包装成 {@link AgentTextExecutor}，
     * 从而让本类与具体执行器实现解耦（默认 {@code ModelStreamTextExecutor::new}，
     * 装配处与测试可注入自定义实现）。包级可见，不对外暴露。
     */
    @FunctionalInterface
    interface AgentScopeExecutorFactory {
        AgentTextExecutor create(Model model);
    }
}