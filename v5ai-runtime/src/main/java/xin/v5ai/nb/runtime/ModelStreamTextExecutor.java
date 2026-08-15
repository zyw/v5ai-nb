package xin.v5ai.nb.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.mcp.McpToolPermission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 直接基于 AgentScope {@link Model} 流式输出的文本执行器。
 *
 * 能力：
 * - 支持多轮对话历史回放（request.history 拼进消息列表）；
 * - 支持注入 RAG 上下文（放进系统提示）；
 * - 发起模型请求前先发 {@code MODEL_CALL} 标记，供运行时转为平台事件；
 * - 可选 MCP 集成：Application 绑定 MCP Server 时，动态注册工具到
 *   HarnessAgent Toolkit，并通过 AgentScope 事件流映射出 ToolCall/ToolResult 事件。
 */
public class ModelStreamTextExecutor implements AgentTextExecutor {
    private final Model model;
    private final McpToolResolver mcpToolResolver;
    private final McpToolCallAuditRepository auditRepository;
    private final Path workspace;

    public ModelStreamTextExecutor(Model model) {
        this(model, null, null, null);
    }

    public ModelStreamTextExecutor(
            Model model,
            McpToolResolver mcpToolResolver,
            McpToolCallAuditRepository auditRepository,
            Path workspace
    ) {
        this.model = model;
        this.mcpToolResolver = mcpToolResolver;
        this.auditRepository = auditRepository;
        this.workspace = workspace;
    }

    @Override
    public Flux<AgentTextEvent> streamText(Application application, AgentRunRequest request) {
        var tools = mcpToolResolver == null ? List.<ResolvedMcpTool>of() : mcpToolResolver.resolveTools(application);
        if (tools.isEmpty()) {
            return streamDirect(application, request);
        }
        return streamWithTools(application, request, tools);
    }

    /**
     * 无 MCP 工具的轻量路径：直接调用模型流式接口。
     */
    private Flux<AgentTextEvent> streamDirect(Application application, AgentRunRequest request) {
        List<Msg> messages = buildMessages(application, request);
        return Flux.concat(
                Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                model.stream(messages, List.of(), generateOptions())
                        .map(ModelStreamTextExecutor::extractText)
                        .filter(text -> text != null && !text.isEmpty())
                        .map(AgentTextEvent.Text::new));
    }

    /**
     * 带 MCP 工具的路径：动态注册工具到 HarnessAgent Toolkit，
     * 订阅 AgentScope 细粒度事件并映射为平台事件。
     */
    private Flux<AgentTextEvent> streamWithTools(
            Application application, AgentRunRequest request, List<ResolvedMcpTool> tools) {
        ensureWorkspaceExists();
        var workspace = this.workspace != null ? this.workspace : Path.of(".agentscope/workspace");

        Toolkit toolkit = new Toolkit();
        Set<String> mcpToolNames = new HashSet<>();
        for (ResolvedMcpTool tool : tools) {
            toolkit.registerAgentTool(new PlatformMcpTool(tool, request.runId(), auditRepository));
            mcpToolNames.add(tool.toolName());
        }
        Map<String, McpToolPermission> toolPermissions = tools.stream()
                .collect(Collectors.toMap(ResolvedMcpTool::toolName, ResolvedMcpTool::permission));

        HarnessAgent agent = HarnessAgent.builder()
                .name(application.name())
                .description("v5ai AgentScope runtime with MCP tools")
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
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .build();

        return Flux.concat(
                        Flux.just(new AgentTextEvent.ModelCall(model.getModelName())),
                        agent.streamEvents(request.query(), RuntimeContext.empty())
                                // mapAgentEvent 对无关事件返回 null：必须用 mapNotNull，
                                // 否则 Reactor 的 map 会对 null 抛 "mapper returned a null value"
                                .mapNotNull(event -> mapAgentEvent(event, mcpToolNames, toolPermissions)))
                .doFinally(signal -> {
                    agent.close();
                    // 同一 Server 的多个工具共享一个连接：按连接去重，只关闭一次
                    tools.stream()
                            .map(ResolvedMcpTool::connection)
                            .distinct()
                            .forEach(McpConnection::close);
                });
    }

    private static AgentTextEvent mapAgentEvent(
            AgentEvent event, Set<String> mcpToolNames, Map<String, McpToolPermission> toolPermissions) {
        if (event instanceof TextBlockDeltaEvent text) {
            return new AgentTextEvent.Text(text.getDelta());
        }
        if (event instanceof ToolCallStartEvent call && mcpToolNames.contains(call.getToolCallName())) {
            var permission = toolPermissions.get(call.getToolCallName());
            return new AgentTextEvent.ToolCall(call.getToolCallName(),
                    permission == null ? McpToolPermission.ALLOW : permission);
        }
        if (event instanceof ToolResultEndEvent result && mcpToolNames.contains(result.getToolCallName())) {
            var success = ToolResultState.SUCCESS.equals(result.getState());
            return new AgentTextEvent.ToolResult(result.getToolCallName(), success,
                    success ? "" : (result.getState() == null ? "tool result error" : result.getState().getValue()));
        }
        if (event instanceof ModelCallStartEvent) {
            // 模型调用标记已在流开头显式发出，避免重复
            return null;
        }
        return null;
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
    private static List<Msg> buildMessages(Application application, AgentRunRequest request) {
        List<Msg> messages = new ArrayList<>();
        var system = new StringBuilder();
        system.append("You are the assistant for application \"").append(application.name()).append("\".");
        if (application.description() != null && !application.description().isBlank()) {
            system.append(' ').append(application.description());
        }
        if (request.ragContext() != null && !request.ragContext().isBlank()) {
            system.append("\n\nUse the following knowledge base context when relevant:\n")
                    .append(request.ragContext());
        }
        messages.add(Msg.builder().role(MsgRole.SYSTEM).textContent(system.toString()).build());

        // 回放历史消息（USER/ASSISTANT 角色映射）
        for (Message history : request.history()) {
            var role = history.role() == MessageRole.USER ? MsgRole.USER : MsgRole.ASSISTANT;
            messages.add(Msg.builder().role(role).textContent(history.content()).build());
        }
        // 追加当前提问
        messages.add(Msg.builder().role(MsgRole.USER).textContent(request.query()).build());
        return messages;
    }

    /**
     * 生成选项：开启流式输出。
     */
    private static GenerateOptions generateOptions() {
        return GenerateOptions.builder().stream(true).build();
    }

    /**
     * 从模型响应中提取文本内容：
     * 拼接所有 TextBlock 的文本；响应为空或无内容时返回 null。
     */
    private static String extractText(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }
        var text = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}
