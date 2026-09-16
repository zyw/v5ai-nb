package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.utils.ChatResponseTexts;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.core.PlatformMcpTool;
import xin.v5ai.nb.common.agentscope.core.RagSearchProvider;
import xin.v5ai.nb.common.agentscope.core.RagSearchTool;
import xin.v5ai.nb.common.agentscope.core.WebSearchTool;
import xin.v5ai.nb.common.agentscope.core.domain.*;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.common.agentscope.core.service.ResolvedSkillAgentSkillService;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * 直接基于 AgentScope {@link Model} 流式输出的文本执行器。
 *
 * 能力：
 * - 支持多轮对话历史回放（request.history 拼进消息列表）；
 * - 支持注入 RAG 上下文（放进系统提示）；
 * - 发起模型请求前先发 {@code MODEL_CALL} 标记，供运行时转为平台事件；
 * - 可选 MCP 集成：AgentDTO 绑定 MCP Server 时，动态注册工具到
 *   HarnessAgent Toolkit，并通过 AgentScope 事件流映射出 ToolCall/ToolResult 事件；
 * - 可选 Skill 集成：AgentDTO 绑定 Skill 时，把已发布版本文件注入 workspace
 *   （{@code skills/<name>/}）并注册只读 Skill 仓库，让 SkillRuntime 把
 *   {@code <available_skills>} 注入系统提示。
 */
public class ModelStreamTextExecutor implements AgentTextExecutor {
    private static final Logger log = LoggerFactory.getLogger(ModelStreamTextExecutor.class);

    private final Model model;
    private final McpToolResolver mcpToolResolver;
    private final McpToolCallAuditService auditService;
    private final SkillWorkspaceResolver skillWorkspaceResolver;
    private final Path workspace;
    private final String webSearchApiKey;
    private final RagSearchProvider ragSearchProvider;

    public ModelStreamTextExecutor(Model model) {
        this(model, null, null, null, null, null);
    }

    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditService,
            Path workspace
    ) {
        this(model, mcpToolResolver, auditService, null, workspace, null);
    }

    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditService,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace
    ) {
        this(model, mcpToolResolver, auditService, skillWorkspaceResolver, workspace, null);
    }

    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditService,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey
    ) {
        this(model, mcpToolResolver, auditService, skillWorkspaceResolver, workspace, webSearchApiKey, null);
    }

    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditService,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey,
            RagSearchProvider ragSearchProvider
    ) {
        this.model = model;
        this.mcpToolResolver = mcpToolResolver;
        this.auditService = auditService;
        this.skillWorkspaceResolver = skillWorkspaceResolver;
        this.workspace = workspace;
        this.webSearchApiKey = webSearchApiKey;
        this.ragSearchProvider = ragSearchProvider;
    }

    @Override
    public Flux<AgentTextEvent> streamText(AgentDTO application, AgentRunBo request) {
        // 能力开关门控：关闭时不加载绑定（MCP/Skill），联网搜索按开关注册独立工具
        var tools = (application.mcpEnabled() && mcpToolResolver != null)
                ? mcpToolResolver.resolveTools(application) : List.<ResolvedMcpTool>of();
        var skills = (application.skillEnabled() && skillWorkspaceResolver != null)
                ? skillWorkspaceResolver.resolveSkills(application) : List.<ResolvedSkill>of();
        boolean webSearch = application.webSearchEnabled();
        // 智能调用：注册 rag_search 工具 + 注入可用知识库列表；强制调用由 AgentScopeRuntime
        // 在 pre-step 检索并把上下文放进 request.ragContext()，此处不再注册工具
        boolean smartRag = isSmartRag(application, request) && ragSearchProvider != null;
        List<KnowledgeBaseRef> knowledgeBases = smartRag
                ? ragSearchProvider.listBoundKnowledgeBases(application.agentKey()) : List.of();
        if (knowledgeBases.isEmpty() && tools.isEmpty() && skills.isEmpty() && !webSearch) {
            return streamDirect(application, request);
        }
        return streamWithExtensions(application, request, tools, skills, webSearch, knowledgeBases);
    }

    /** 是否走智能调用工具路径：仅当 ragEnabled 且 rag_call_mode=SMART 且请求未携带强制检索上下文。 */
    private static boolean isSmartRag(AgentDTO application, AgentRunBo request) {
        if (!application.ragEnabled() || application.ragCallMode() != RagCallMode.SMART.value()) {
            return false;
        }
        return request.ragContext() == null || request.ragContext().isBlank();
    }

    /**
     * 无 MCP/Skill 的轻量路径：直接调用模型流式接口。
     */
    private Flux<AgentTextEvent> streamDirect(AgentDTO application, AgentRunBo request) {
        List<Msg> messages = buildMessages(application, request);
        return Flux.concat(
                Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                model.stream(messages, List.of(), generateOptions())
                        .map(ChatResponseTexts::textOf)
                        .filter(text -> text != null && !text.isEmpty())
                        .map(AgentTextEvent.Text::new));
    }

    /**
     * 带 MCP 工具 / Skill 的路径：动态注册工具到 HarnessAgent Toolkit、
     * 把 Skill 文件注入 workspace 并注册 Skill 仓库，订阅 AgentScope 细粒度事件
     * 并映射为平台事件。
     */
    private Flux<AgentTextEvent> streamWithExtensions(
            AgentDTO application, AgentRunBo request,
            List<ResolvedMcpTool> tools, List<ResolvedSkill> skills, boolean webSearch,
            List<KnowledgeBaseRef> knowledgeBases) {
        ensureWorkspaceExists();
        var workspace = this.workspace != null ? this.workspace : Path.of(".agentscope/workspace");

        Toolkit toolkit = new Toolkit();
        Set<String> toolNames = new HashSet<>();
        for (ResolvedMcpTool tool : tools) {
            toolkit.registerAgentTool(new PlatformMcpTool(tool, request.runId(), auditService));
            toolNames.add(tool.toolName());
        }
        Map<String, McpToolPermission> toolPermissions = tools.stream()
                .collect(Collectors.toMap(ResolvedMcpTool::toolName, ResolvedMcpTool::permission));
        // 联网搜索：独立内置工具，权限视为 ALLOW，事件与 MCP 工具同一口径
        if (webSearch) {
            toolkit.registerAgentTool(new WebSearchTool(webSearchApiKey));
            toolNames.add(WebSearchTool.TOOL_NAME);
            toolPermissions.put(WebSearchTool.TOOL_NAME, McpToolPermission.ALLOW);
        }
        // 智能调用 RAG：注册 rag_search 工具；命中通过 outbox 回传，在 TOOL_RESULT 后追加 RETRIEVAL
        Deque<List<RagHit>> ragOutbox = new ConcurrentLinkedDeque<>();
        if (!knowledgeBases.isEmpty()) {
            toolkit.registerAgentTool(new RagSearchTool(application.agentKey(), ragSearchProvider, ragOutbox::addLast));
            toolNames.add(RagSearchTool.TOOL_NAME);
            toolPermissions.put(RagSearchTool.TOOL_NAME, McpToolPermission.ALLOW);
        }

        // Workspace 注入：把已发布 Skill 文件写入 <workspace>/skills/<name>/，便于
        // 工作区工具读取资源；同时注册只读 Skill 仓库让 SkillRuntime 注入提示。
        injectSkillsToWorkspace(workspace, skills);

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(application.name())
                .description("v5ai AgentScope runtime with MCP tools and skills")
                // MCP/Skill 路径不走 buildMessages，须显式注入系统提示（含 RAG 上下文/可用知识库列表），
                // 否则 application.systemPrompt() 与 request.ragContext() 都会丢失
                .sysPrompt(buildSystemPrompt(application, request, knowledgeBases))
                .model(model)
                // 每次运行使用全新内存状态存储：上一次运行中断（如工具调用未完成）
                // 残留的 pending tool call 不会泄漏到本次运行，避免
                // "Pending tool calls exist without results" 报错
                .stateStore(new InMemoryAgentStateStore())
                .enablePendingToolRecovery(true)
                .workspace(workspace)
                .toolkit(toolkit)
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableSubagents()
                .disableDynamicSubagents();

        HarnessAgent agent;
        if (skills.isEmpty()) {
            agent = builder
                    .disableDynamicSkills()
                    .disableDefaultWorkspaceSkills()
                    .build();
        } else {
            // 有 Skill：注册只读 Skill 仓库（唯一技能来源，隔离其它应用/历史残留），
            // 并保持动态技能启用（HarnessSkillMiddleware 注入 <available_skills>）
            agent = builder
                    .skillRepository(new ResolvedSkillAgentSkillService(skills))
                    .build();
        }

        return Flux.concat(
                        Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                        agent.streamEvents(conversationMessages(request), RuntimeContext.empty())
                                // mapAgentEvents 对无关事件返回空列表；rag_search 的 ToolResult
                                // 会展开为 ToolResult + Retrieval 两个事件
                                .concatMap(event -> Flux.fromIterable(
                                        mapAgentEvents(event, toolNames, toolPermissions, ragOutbox))))
                .doFinally(signal -> {
                    agent.close();
                    // 同一 Server 的多个工具共享一个连接：按连接去重，只关闭一次
                    tools.stream()
                            .map(ResolvedMcpTool::connection)
                            .distinct()
                            .forEach(McpConnection::close);
                });
    }

    /**
     * 把 Skill 文件写入 {@code <workspace>/skills/<skillName>/}。best-effort：
     * 单文件失败仅告警，不中断运行。
     */
    private static void injectSkillsToWorkspace(Path workspace, List<ResolvedSkill> skills) {
        if (skills.isEmpty()) {
            return;
        }
        Path skillsDir = workspace.resolve("skills");
        try {
            Files.createDirectories(skillsDir);
            for (ResolvedSkill skill : skills) {
                Path skillDir = skillsDir.resolve(skill.skillName());
                Files.createDirectories(skillDir);
                for (Map.Entry<String, String> file : skill.files().entrySet()) {
                    try {
                        Path target = skillDir.resolve(file.getKey());
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
                    } catch (IOException exception) {
                        log.warn("failed to inject skill file '{}' for '{}': {}",
                                file.getKey(), skill.skillName(), exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            log.warn("failed to inject skills into workspace {}: {}", skillsDir, exception.getMessage());
        }
    }

    private static List<AgentTextEvent> mapAgentEvents(
            AgentEvent event, Set<String> toolNames, Map<String, McpToolPermission> toolPermissions,
            Deque<List<RagHit>> ragOutbox) {
        if (event instanceof ThinkingBlockDeltaEvent thinking) {
            // 模型推理（思考）增量：与回答分流，供前端折叠展示；上游不落库
            return List.of(new AgentTextEvent.Reasoning(thinking.getDelta()));
        }
        if (event instanceof TextBlockDeltaEvent text) {
            return List.of(new AgentTextEvent.Text(text.getDelta()));
        }
        if (event instanceof ToolCallStartEvent call && toolNames.contains(call.getToolCallName())) {
            var permission = toolPermissions.get(call.getToolCallName());
            return List.of(new AgentTextEvent.ToolCall(call.getToolCallName(),
                    permission == null ? McpToolPermission.ALLOW : permission));
        }
        if (event instanceof ToolResultEndEvent result && toolNames.contains(result.getToolCallName())) {
            var success = ToolResultState.SUCCESS.equals(result.getState());
            var toolResult = new AgentTextEvent.ToolResult(result.getToolCallName(), success,
                    success ? "" : (result.getState() == null ? "tool result error" : result.getState().getValue()));
            // rag_search 命中在工具内部写入 outbox，TOOL_RESULT 之后追加 RETRIEVAL 事件
            if (RagSearchTool.TOOL_NAME.equals(result.getToolCallName())) {
                var hits = ragOutbox.pollFirst();
                if (hits != null && !hits.isEmpty()) {
                    return List.of(toolResult, new AgentTextEvent.Retrieval(hits));
                }
            }
            return List.of(toolResult);
        }
        if (event instanceof ModelCallStartEvent) {
            // 模型调用标记已在流开头显式发出，避免重复
            return List.of();
        }
        return List.of();
    }

    private void ensureWorkspaceExists() {
        try {
            Files.createDirectories(workspace != null ? workspace : Path.of(".agentscope/workspace"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create AgentScope workspace: " + workspace, exception);
        }
    }

    /**
     * 组装发送给模型的完整消息列表：
     * 1) SYSTEM 提示：应用身份 + 描述 + 可选的 RAG 知识上下文；
     * 2) 历史消息：按角色映射为 USER/ASSISTANT；
     * 3) 当前用户提问。
     */
    private static List<Msg> buildMessages(AgentDTO application, AgentRunBo request) {
        List<Msg> messages = new ArrayList<>();
        messages.add(Msg.builder().role(MsgRole.SYSTEM).textContent(buildSystemPrompt(application, request)).build());
        messages.addAll(conversationMessages(request));
        return messages;
    }

    /**
     * 组装对话消息（不含系统提示）：历史 USER/ASSISTANT 轮次 + 当前提问。
     * 直连路径经 {@link #buildMessages} 复用，MCP/Skill/联网搜索路径经
     * {@code streamEvents(List, ...)} 复用，两路历史回放口径一致（记忆由运行时注入历史）。
     */
    private static List<Msg> conversationMessages(AgentRunBo request) {
        List<Msg> messages = new ArrayList<>();
        for (SessionMessage history : request.history()) {
            var role = history.role() == MessageRole.USER ? MsgRole.USER : MsgRole.ASSISTANT;
            messages.add(Msg.builder().role(role).textContent(history.content()).build());
        }
        messages.add(Msg.builder().role(MsgRole.USER).textContent(request.query()).build());
        return messages;
    }

    /**
     * 组装系统提示：应用系统提示词（或默认身份+描述）+ RAG 知识上下文。
     * 直连路径经 buildMessages 注入，MCP/Skill 路径经 HarnessAgent.sysPrompt 注入，两路共用同一口径。
     */
    private static String buildSystemPrompt(AgentDTO application, AgentRunBo request) {
        return buildSystemPrompt(application, request, null);
    }

    /**
     * 组装系统提示：应用系统提示词（或默认身份+描述）+ RAG 知识上下文；
     * 智能调用模式下额外注入「可用知识库列表」（{@code knowledgeBases}），供模型决定何时调用 {@code rag_search}。
     * 直连路径经 buildMessages 注入，MCP/Skill 路径经 HarnessAgent.sysPrompt 注入，两路共用同一口径。
     */
    private static String buildSystemPrompt(AgentDTO application, AgentRunBo request,
                                            List<KnowledgeBaseRef> knowledgeBases) {
        var system = new StringBuilder();
        // 配置了系统提示词则优先使用，否则回退默认（应用身份 + 描述）
        if (application.systemPrompt() != null && !application.systemPrompt().isBlank()) {
            system.append(application.systemPrompt());
        } else {
            system.append("You are the assistant for application \"").append(application.name()).append("\".");
            if (application.description() != null && !application.description().isBlank()) {
                system.append(' ').append(application.description());
            }
        }
        if (request.ragContext() != null && !request.ragContext().isBlank()) {
            system.append("\n\nUse the following knowledge base context when relevant:\n")
                    .append(request.ragContext());
        }
        if (knowledgeBases != null && !knowledgeBases.isEmpty()) {
            system.append("\n\nAvailable knowledge bases. When the user's question needs grounded knowledge, "
                    + "call rag_search with the relevant 'ragId' and your 'queryQuestion' "
                    + "(you may call it multiple times if useful):\n");
            for (KnowledgeBaseRef kb : knowledgeBases) {
                system.append("- id=").append(kb.id()).append(": ").append(kb.name());
                if (kb.description() != null && !kb.description().isBlank()) {
                    system.append(" (").append(kb.description()).append(')');
                }
                system.append('\n');
            }
        }
        return system.toString();
    }

    /**
     * 生成选项：开启流式输出。
     */
    private static GenerateOptions generateOptions() {
        return GenerateOptions.builder().stream(true).build();
    }

}

