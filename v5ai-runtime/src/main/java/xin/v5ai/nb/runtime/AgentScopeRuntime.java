package xin.v5ai.nb.runtime;

import java.util.UUID;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.PublishedAgentResolver;
import xin.v5ai.nb.mcp.McpToolPermission;

/**
 * AgentScope 运行时：{@link AgentRuntime} 的默认实现。
 *
 * 流程编排：解析 Agent（正式路径仅已发布；也可注入调试解析器允许草稿态）→ 生成 runId →
 * 可选构建 RAG 上下文 → 委托 AgentTextExecutor 流式执行 → 把内部事件映射为统一 RuntimeEvent。
 * 整个执行是惰性的（Flux.defer），订阅时才真正开始运行。
 */
public class AgentScopeRuntime implements AgentRuntime {
    /** Agent 解析器：根据 agentKey 得到 Agent 配置（正式路径要求已发布，调试路径允许草稿） */
    private final Function<String, Agent> applicationResolver;
    /** 底层文本执行器：真正驱动 Agent 跑模型 */
    private final AgentTextExecutor agentTextExecutor;
    /** RAG 上下文构建器（可空，为空则不做知识检索） */
    private final RagContextProvider ragContextProvider;

    /**
     * 无 RAG 的构造器：不配置知识检索能力；仅解析已发布 Agent。
     */
    public AgentScopeRuntime(PublishedAgentResolver applicationResolver, AgentTextExecutor agentTextExecutor) {
        this(applicationResolver::resolve, agentTextExecutor, null);
    }

    /**
     * 全参构造：可附带 RAG 上下文构建器；仅解析已发布 Agent。
     */
    public AgentScopeRuntime(PublishedAgentResolver applicationResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider) {
        this(applicationResolver::resolve, agentTextExecutor, ragContextProvider);
    }

    /**
     * 自定义解析器构造：支持传入调试解析器（如允许草稿态的 {@code resolveForDebug}）。
     */
    public AgentScopeRuntime(Function<String, Agent> applicationResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider) {
        this.applicationResolver = applicationResolver;
        this.agentTextExecutor = agentTextExecutor;
        this.ragContextProvider = ragContextProvider;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        // defer：延迟到订阅时才解析应用、生成 runId，避免每次构建都执行副作用
        return Flux.defer(() -> {
            var application = applicationResolver.apply(request.agentKey());
            var runId = UUID.randomUUID().toString();
            // 若未配置 RAG，则上下文为空串；否则异步构建检索上下文
            var contextMono = ragContextProvider == null
                    ? Mono.just("")
                    : ragContextProvider.buildContext(application, request);
            return Flux.concat(
                    // 1) 运行开始事件
                    Flux.just(RuntimeEvent.started(runId)),
                    // 2) 拿到上下文后执行 Agent
                    contextMono.flatMapMany(context -> {
                        // 有有效上下文则拼进请求，否则用原请求
                        var enriched = (context == null || context.isBlank())
                                ? request.withRunId(runId)
                                : request.withRagContext(context).withRunId(runId);
                        // 将内部事件统一映射为平台 RuntimeEvent（工具事件可展开为多个事件）
                        var events = agentTextExecutor.streamText(application, enriched)
                                .flatMap(event -> toRuntimeEvents(runId, event));
                        // 有检索上下文时，在 Agent 输出之前先发一个 RETRIEVAL 事件
                        if (context != null && !context.isBlank()) {
                            return Flux.concat(Flux.just(RuntimeEvent.retrieval(runId, context)), events);
                        }
                        return events;
                    }),
                    // 3) 运行结束事件
                    Flux.just(RuntimeEvent.completed(runId)));
        });
    }

    @Override
    public Mono<AgentRunResult> call(AgentRunRequest request) {
        // 复用 stream()，只保留文本增量片段并拼接为完整回答；
        // 注意：这里 runId 与 stream 事件流里的不同，是 call 自己生成的
        return stream(request)
                .filter(event -> event.type() == RuntimeEventType.TEXT_DELTA)
                .map(RuntimeEvent::payload)
                .collectList()
                .map(parts -> new AgentRunResult(UUID.randomUUID().toString(), String.join("", parts)));
    }

    /**
     * 把执行器内部事件映射为平台事件流。
     */
    private static Flux<RuntimeEvent> toRuntimeEvents(String runId, AgentTextEvent event) {
        return switch (event) {
            case AgentTextEvent.Text text -> Flux.just(RuntimeEvent.textDelta(runId, text.text()));
            case AgentTextEvent.ModelCall call -> Flux.just(RuntimeEvent.modelCall(runId, call.modelName()));
            case AgentTextEvent.ToolCall call -> {
                var callEvent = Flux.just(RuntimeEvent.toolCall(runId, toolCallPayload(call)));
                // APPROVE 决策：先发出 PERMISSION_REQUIRED 事件（人工审批提示），随后自动放行
                if (call.permission() == McpToolPermission.APPROVE) {
                    yield Flux.concat(callEvent, Flux.just(RuntimeEvent.permissionRequired(runId,
                            "tool '" + call.toolName() + "' requires approval (auto-approved)")));
                }
                yield callEvent;
            }
            case AgentTextEvent.ToolResult result -> Flux.just(RuntimeEvent.toolResult(runId,
                    result.success() ? result.toolName() : result.toolName() + " failed: " + result.message()));
        };
    }

    private static String toolCallPayload(AgentTextEvent.ToolCall call) {
        return "{\"tool\":\"" + call.toolName() + "\",\"permission\":\"" + call.permission() + "\"}";
    }
}
