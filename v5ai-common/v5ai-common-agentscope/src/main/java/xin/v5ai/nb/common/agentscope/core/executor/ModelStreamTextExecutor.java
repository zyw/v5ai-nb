package xin.v5ai.nb.common.agentscope.core.executor;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.core.RagSearchProvider;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.KnowledgeBaseRef;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedMcpTool;
import xin.v5ai.nb.common.agentscope.core.domain.ResolvedSkill;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.repository.ResolvedSkillAgentSkillRepository;
import xin.v5ai.nb.common.agentscope.core.resolver.McpToolResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.SkillWorkspaceResolver;
import xin.v5ai.nb.common.agentscope.core.service.AttachmentContentProvider;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.common.agentscope.core.tools.PlatformMcpTool;
import xin.v5ai.nb.common.agentscope.core.tools.RagSearchTool;
import xin.v5ai.nb.common.agentscope.core.tools.WebSearchTool;
import xin.v5ai.nb.common.agentscope.core.token.PromptTokenEstimator;
import xin.v5ai.nb.common.agentscope.core.token.TokenUsageAccumulator;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.common.agentscope.utils.ChatResponseTexts;

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
 * - 可选 MCP 集成：Agent 绑定 MCP Server 时，动态注册工具到
 *   HarnessAgent Toolkit，并通过 AgentScope 事件流映射出 ToolCall/ToolResult 事件；
 * - 可选 Skill 集成：Agent 绑定 Skill 时，把已发布版本文件注入 workspace
 *   （{@code skills/<name>/}）并注册只读 Skill 仓库，让 SkillRuntime 把
 *   {@code <available_skills>} 注入系统提示；
 * - 可选多模态：消息携带图片附件时，把历史与当前提问组装成「文本块 + 图片块」。
 */
@Slf4j
public class ModelStreamTextExecutor implements AgentTextExecutor {

    /** 历史回放中图片的默认累计体积预算：16MB（见 v5ai-nb#6 Q21）。 */
    public static final long DEFAULT_HISTORY_IMAGE_BUDGET_BYTES = 16L * 1024 * 1024;

    /** 图片未能进入上下文时的文字占位。 */
    static final String IMAGE_PLACEHOLDER = "[图片]";

    /**
     * 每张图片在 token 估算里折算的 token 数（默认 1024）：见
     * {@link PromptTokenEstimator#DEFAULT_IMAGE_TOKENS}。只影响「模型没回报用量」时的兜底口径。
     */
    public static final long DEFAULT_IMAGE_TOKENS = PromptTokenEstimator.DEFAULT_IMAGE_TOKENS;

    private final Model model;
    private final McpToolResolver mcpToolResolver;
    private final McpToolCallAuditService auditService;
    private final SkillWorkspaceResolver skillWorkspaceResolver;
    private final Path workspace;
    private final String webSearchApiKey;
    private final RagSearchProvider ragSearchProvider;
    /** 附件内容读取端口（可空）：为空时消息上的附件被忽略，退化为纯文本。 */
    private final AttachmentContentProvider attachmentContentProvider;
    /** 历史回放的图片体积预算（字节），按最近优先消费。 */
    private final long historyImageBudgetBytes;
    /** 图片折算的 token 数（仅用于用量估算兜底，见 {@link #DEFAULT_IMAGE_TOKENS}）。 */
    private final long imageTokensPerImage;

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
        this(model, mcpToolResolver, auditService, skillWorkspaceResolver, workspace, webSearchApiKey,
                ragSearchProvider, null, DEFAULT_HISTORY_IMAGE_BUDGET_BYTES);
    }

    /**
     * 全参构造：在上一构造的基础上装配附件内容读取端口与图片回放预算，
     * 使消息上的图片附件真正进入模型输入。
     *
     * @param attachmentContentProvider 附件读取端口；为 {@code null} 时附件被忽略（纯文本行为）
     * @param historyImageBudgetBytes   历史回放中图片的累计体积预算（字节），按最近优先消费
     */
    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditService,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey,
            RagSearchProvider ragSearchProvider,
            AttachmentContentProvider attachmentContentProvider,
            long historyImageBudgetBytes
    ) {
        this(model, mcpToolResolver, auditService, skillWorkspaceResolver, workspace, webSearchApiKey,
                ragSearchProvider, attachmentContentProvider, historyImageBudgetBytes, DEFAULT_IMAGE_TOKENS);
    }

    /**
     * 全参构造 + 用量估算参数：在上一构造的基础上指定每张图片折算的 token 数。
     *
     * @param imageTokensPerImage 图片折算的 token 数（用量估算兜底用；{@code <= 0} 表示不计图片）
     */
    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditService auditService,
            SkillWorkspaceResolver skillWorkspaceResolver,
            Path workspace,
            String webSearchApiKey,
            RagSearchProvider ragSearchProvider,
            AttachmentContentProvider attachmentContentProvider,
            long historyImageBudgetBytes,
            long imageTokensPerImage
    ) {
        this.model = model;
        this.mcpToolResolver = mcpToolResolver;
        this.auditService = auditService;
        this.skillWorkspaceResolver = skillWorkspaceResolver;
        this.workspace = workspace;
        this.webSearchApiKey = webSearchApiKey;
        this.ragSearchProvider = ragSearchProvider;
        this.attachmentContentProvider = attachmentContentProvider;
        this.historyImageBudgetBytes = historyImageBudgetBytes;
        this.imageTokensPerImage = imageTokensPerImage;
    }

    @Override
    public Flux<AgentTextEvent> streamText(AgentDTO agentDTO, AgentRunBo request) {
        // 能力开关门控：关闭时不加载绑定（MCP/Skill），联网搜索按开关注册独立工具
        // 收窄项按请求生效：把本次运行显式禁用的 Server/Skill 在解析阶段就排除掉
        var tools = (agentDTO.mcpEnabled() && mcpToolResolver != null)
                ? mcpToolResolver.resolveTools(agentDTO, request.disabledMcpServerIds())
                : List.<ResolvedMcpTool>of();
        var skills = (agentDTO.skillEnabled() && skillWorkspaceResolver != null)
                ? skillWorkspaceResolver.resolveSkills(agentDTO, request.disabledSkillIds())
                : List.<ResolvedSkill>of();
        // 联网：Agent 开关为准，请求只能显式关闭（门户开关关闭时不注册 WebSearchTool）
        boolean webSearch = request.resolveWebSearch(agentDTO.webSearchEnabled());
        // 智能调用：注册 rag_search 工具 + 注入可用知识库列表；强制调用由 AgentScopeRuntime
        // 在 pre-step 检索并把上下文放进 request.ragContext()，此处不再注册工具
        boolean smartRag = isSmartRag(agentDTO, request) && ragSearchProvider != null;
        List<KnowledgeBaseRef> knowledgeBases = smartRag
                ? ragSearchProvider.listBoundKnowledgeBases(agentDTO.agentKey()) : List.of();
        if (knowledgeBases.isEmpty() && tools.isEmpty() && skills.isEmpty() && !webSearch) {
            return streamDirect(agentDTO, request);
        }
        return streamWithExtensions(agentDTO, request, tools, skills, webSearch, knowledgeBases);
    }

    /** 是否走智能调用工具路径：仅当 ragEnabled 且 rag_call_mode=SMART 且请求未携带强制检索上下文。 */
    private static boolean isSmartRag(AgentDTO agentDTO, AgentRunBo request) {
        if (!agentDTO.ragEnabled() || agentDTO.ragCallMode() != RagCallMode.SMART.value()) {
            return false;
        }
        return request.ragContext() == null || request.ragContext().isBlank();
    }

    /**
     * 无 MCP/Skill 的轻量路径：直接调用模型流式接口。
     */
    private Flux<AgentTextEvent> streamDirect(AgentDTO agentDTO, AgentRunBo request) {
        List<Msg> messages = buildMessages(agentDTO, request);
        var usage = new TokenUsageAccumulator();
        return Flux.concat(
                Flux.just(new AgentTextEvent.ModelCall(agentDTO.modelId(), model.getModelName(), model.getModelName()), estimatedUsage(messages)),
                model.stream(messages, List.of(), generateOptions())
                        .map(response -> {
                            // 直连路径整个流就是一次模型调用：用量通常只出现在最后一块上，
                            // 即使服务端每块都带（累计值），取最后一次也仍然正确
                            usage.setCall(response == null ? null : response.getUsage());
                            return ChatResponseTexts.textOf(response);
                        })
                        .filter(text -> text != null && !text.isEmpty())
                        .map(AgentTextEvent.Text::new),
                realUsage(usage));
    }

    /**
     * 发送前的估算用量（{@code estimated=true}）：模型没有回报用量时的兜底口径，
     * 也记录在流开头以便上游在提前中断时仍有数可用。
     */
    private AgentTextEvent estimatedUsage(List<Msg> messages) {
        return new AgentTextEvent.Usage(
                PromptTokenEstimator.estimateMessages(messages, imageTokensPerImage), 0, true);
    }

    /**
     * 流末尾追加真实用量事件（{@code estimated=false}）：服务端一次都没回报时保持空流。
     */
    private static Flux<AgentTextEvent> realUsage(TokenUsageAccumulator usage) {
        return Flux.defer(() -> {
            var event = usage.toEvent();
            return event == null ? Flux.<AgentTextEvent>empty() : Flux.just(event);
        });
    }

    /**
     * 带 MCP 工具 / Skill 的路径：动态注册工具到 HarnessAgent Toolkit、
     * 把 Skill 文件注入 workspace 并注册 Skill 仓库，订阅 AgentScope 细粒度事件
     * 并映射为平台事件。
     */
    private Flux<AgentTextEvent> streamWithExtensions(
            AgentDTO agentDTO, AgentRunBo request,
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
            toolkit.registerAgentTool(new RagSearchTool(agentDTO.agentKey(), ragSearchProvider, ragOutbox::addLast));
            toolNames.add(RagSearchTool.TOOL_NAME);
            toolPermissions.put(RagSearchTool.TOOL_NAME, McpToolPermission.ALLOW);
        }

        // Workspace 注入：把已发布 Skill 文件写入 <workspace>/skills/<name>/，便于
        // 工作区工具读取资源；同时注册只读 Skill 仓库让 SkillRuntime 注入提示。
        injectSkillsToWorkspace(workspace, skills);

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(agentDTO.name())
                .description("v5ai AgentScope runtime with MCP tools and skills")
                // MCP/Skill 路径不走 buildMessages，须显式注入系统提示（含 RAG 上下文/可用知识库列表），
                // 否则 application.systemPrompt() 与 request.ragContext() 都会丢失
                .sysPrompt(buildSystemPrompt(agentDTO, request, knowledgeBases))
                .model(model)
                // 每次运行使用全新内存状态存储：上一次运行中断（如工具调用未完成）
                // 残留的 pending tool call 不会泄漏到本次运行，避免
                // "Pending tool calls exist without results" 报错
                .stateStore(new InMemoryAgentStateStore())
                .enablePendingToolRecovery(true)
                .workspace(workspace)
                .toolkit(toolkit)
                // 禁用文件系统工具
                .disableFilesystemTools()
                // 禁用 Shell 工具
                .disableShellTool()
                // 禁用记忆工具
                .disableMemoryTools()
                // 禁用会话持久化
                .disableSessionPersistence()
                // 禁用工作区上下文
                .disableWorkspaceContext()
                // 禁用子代理
                .disableSubagents()
                // 禁用动态子代理
                .disableDynamicSubagents();

        HarnessAgent agent;
        if (skills.isEmpty()) {
            agent = builder
                    // 禁用动态技能
                    .disableDynamicSkills()
                    // 禁用默认工作区技能
                    .disableDefaultWorkspaceSkills()
                    .build();
        } else {
            // 有 Skill：注册只读 Skill 仓库（唯一技能来源，隔离其它 Agent/历史残留），
            // 并保持动态技能启用（HarnessSkillMiddleware 注入 <available_skills>）
            agent = builder
                    .skillRepository(new ResolvedSkillAgentSkillRepository(skills))
                    .build();
        }

        // 消息列表只构建一次：构建过程会按预算真正读取图片字节，重复构建等于重复 IO
        List<Msg> messages = conversationMessages(request);
        var usage = new TokenUsageAccumulator();
        return Flux.concat(
                        Flux.just(new AgentTextEvent.ModelCall(agentDTO.modelId(), model.getModelName(), model.getModelName()), estimatedUsage(messages)),
                        agent.streamEvents(messages, RuntimeContext.empty())
                                .concatMap(event -> {
                                    // 每次模型调用结束都会带一条真实用量：工具循环下会有多轮，累加
                                    if (event instanceof ModelCallEndEvent end) {
                                        usage.addCall(end.getUsage());
                                    }
                                    // mapAgentEvents 对无关事件返回空列表；rag_search 的 ToolResult
                                    // 会展开为 ToolResult + Retrieval 两个事件
                                    return Flux.fromIterable(
                                            mapAgentEvents(event, toolNames, toolPermissions, ragOutbox));
                                }),
                        realUsage(usage))
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
     * 1) SYSTEM 提示：Agent 身份 + 描述 + 可选的 RAG 知识上下文；
     * 2) 历史消息：按角色映射为 USER/ASSISTANT；
     * 3) 当前用户提问。
     */
    private List<Msg> buildMessages(AgentDTO application, AgentRunBo request) {
        List<Msg> messages = new ArrayList<>();
        messages.add(Msg.builder().role(MsgRole.SYSTEM).textContent(buildSystemPrompt(application, request)).build());
        messages.addAll(conversationMessages(request));
        return messages;
    }

    /**
     * 组装对话消息（不含系统提示）：历史 USER/ASSISTANT 轮次 + 当前提问。
     * 直连路径经 {@link #buildMessages} 复用，MCP/Skill/联网搜索路径经
     * {@code streamEvents(List, ...)} 复用，两路历史回放口径一致（记忆由运行时注入历史）。
     *
     * <p>带附件的消息按多模态构造：文本块 + 若干图片块。图片按**最近优先**消费体积预算，
     * 预算用尽（或图片缺失/读取失败）时退化为 {@code [图片]} 文字占位，保证请求体不会无限膨胀
     * （见 v5ai-nb#6 Q21：更早的图片会从上下文里消失，模型答不出"最开始那张图"）。</p>
     */
    private List<Msg> conversationMessages(AgentRunBo request) {
        // 由新到旧决定每一张图的去留，最后再反转回时间顺序
        var remainingBytes = new long[]{historyImageBudgetBytes};
        var newestFirst = new ArrayList<Msg>(request.history().size() + 1);
        newestFirst.add(toMessage(MsgRole.USER, request.query(), request.attachments(), remainingBytes));
        for (int i = request.history().size() - 1; i >= 0; i--) {
            SessionMessage history = request.history().get(i);
            var role = history.role() == MessageRole.USER ? MsgRole.USER : MsgRole.ASSISTANT;
            newestFirst.add(toMessage(role, history.content(), history.attachments(), remainingBytes));
        }
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /**
     * 构造单条消息：无可用图片时退化为纯文本（与历史行为完全一致）。
     */
    private Msg toMessage(MsgRole role, String text, List<AttachmentRef> attachments, long[] remainingBytes) {
        var plain = text == null ? "" : text;
        if (attachments.isEmpty() || attachmentContentProvider == null) {
            return Msg.builder().role(role).textContent(plain).build();
        }
        var blocks = new ArrayList<ContentBlock>();
        if (!plain.isEmpty()) {
            blocks.add(TextBlock.builder().text(plain).build());
        }
        for (AttachmentRef attachment : attachments) {
            if (attachment == null || !attachment.isImage()) {
                continue;
            }
            var image = readImage(attachment, remainingBytes);
            blocks.add(image != null ? image : TextBlock.builder().text(IMAGE_PLACEHOLDER).build());
        }
        if (blocks.isEmpty()) {
            return Msg.builder().role(role).textContent(plain).build();
        }
        return Msg.builder().role(role).content(blocks).build();
    }

    /**
     * 读取一张图片作为图片块。
     *
     * <p>预算是硬边界：预算已用尽时**根本不去读字节**——既省 IO，也让「更早的图」确定性地降级；
     * 单张就超过剩余预算、或读取失败时同样降级为占位。</p>
     *
     * @return 图片块；该图不进入上下文时返回 {@code null}（调用方补占位）
     */
    private ContentBlock readImage(AttachmentRef attachment, long[] remainingBytes) {
        if (remainingBytes[0] <= 0 || attachment.resourceId() == null) {
            return null;
        }
        try {
            var content = attachmentContentProvider.read(attachment.resourceId());
            if (content == null || content.bytes() == null || content.bytes().length == 0
                    || content.bytes().length > remainingBytes[0]) {
                return null;
            }
            remainingBytes[0] -= content.bytes().length;
            return ImageBlock.builder()
                    .source(Base64Source.builder()
                            .mediaType(content.mimeType() == null || content.mimeType().isBlank()
                                    ? "image/png" : content.mimeType())
                            .data(Base64.getEncoder().encodeToString(content.bytes()))
                            .build())
                    .build();
        } catch (Exception exception) {
            log.warn("failed to load attachment {} into model context: {}",
                    attachment.resourceId(), exception.getMessage());
            return null;
        }
    }

    /**
     * 组装系统提示：Agent 系统提示词（或默认身份+描述）+ RAG 知识上下文。
     * 直连路径经 buildMessages 注入，MCP/Skill 路径经 HarnessAgent.sysPrompt 注入，两路共用同一口径。
     */
    private static String buildSystemPrompt(AgentDTO application, AgentRunBo request) {
        return buildSystemPrompt(application, request, null);
    }

    /**
     * 组装系统提示：Agent 系统提示词（或默认身份+描述）+ RAG 知识上下文；
     * 智能调用模式下额外注入「可用知识库列表」（{@code knowledgeBases}），供模型决定何时调用 {@code rag_search}。
     * 直连路径经 buildMessages 注入，MCP/Skill 路径经 HarnessAgent.sysPrompt 注入，两路共用同一口径。
     */
    private static String buildSystemPrompt(AgentDTO application, AgentRunBo request,
                                            List<KnowledgeBaseRef> knowledgeBases) {
        var system = new StringBuilder();
        // 配置了系统提示词则优先使用，否则回退默认（Agent 身份 + 描述）
        if (application.systemPrompt() != null && !application.systemPrompt().isBlank()) {
            system.append(application.systemPrompt());
        } else {
            system.append("You are the assistant for application \"").append(application.name()).append("\".");
            if (application.description() != null && !application.description().isBlank()) {
                system.append(' ').append(application.description());
            }
        }
        // 会话摘要：本会话更早内容的压缩版（历史窗口之外）。放在 RAG 之前——它是「本会话自己」的
        // 上下文，比外部知识更贴近当前对话
        if (request.summaryContext() != null && !request.summaryContext().isBlank()) {
            system.append("\n\n以下是本会话更早内容的摘要（更早的消息已被裁剪，供你保持连贯，"
                    + "不要逐字复述，也不要把它当作本轮的新信息）：\n")
                    .append(request.summaryContext());
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
