package xin.v5ai.nb.common.agentscope.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunRequest;
import xin.v5ai.nb.common.agentscope.core.domain.AgentRunResult;
import xin.v5ai.nb.common.agentscope.core.domain.RuntimeEvent;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * AgentScope 运行时：{@link AgentRuntime} 的默认实现。
 *
 * 流程编排：解析 Agent（正式路径仅已发布；也可注入调试解析器允许草稿态）→ 生成 runId →
 * 可选构建 RAG 上下文 → 委托 AgentTextExecutor 流式执行 → 把内部事件映射为统一 RuntimeEvent。
 * 整个执行是惰性的（Flux.defer），订阅时才真正开始运行。
 */
public class AgentScopeRuntime implements AgentRuntime {
    /** 单条引用内容截断长度：与知识库问答一致，避免单条 SSE 载荷过大。 */
    private static final int HIT_CONTENT_CAP = 2_000;

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
            // 若未配置 RAG，则上下文为空；否则异步构建检索上下文（含结构化命中）
            var contextMono = ragContextProvider == null
                    ? Mono.just(RagContext.empty())
                    : ragContextProvider.buildContext(application, request);
            return Flux.concat(
                    // 1) 运行开始事件
                    Flux.just(RuntimeEvent.started(runId)),
                    // 2) 拿到检索结果后执行 Agent
                    contextMono.flatMapMany(rag -> {
                        // 有有效上下文则拼进请求，否则用原请求
                        var enriched = rag.isEmpty()
                                ? request.withRunId(runId)
                                : request.withRagContext(rag.context()).withRunId(runId);
                        // 将内部事件统一映射为平台 RuntimeEvent（工具事件可展开为多个事件）
                        var events = agentTextExecutor.streamText(application, enriched)
                                .flatMap(event -> toRuntimeEvents(runId, event));
                        // 有检索上下文时，在 Agent 输出之前先发一个 RETRIEVAL 事件（载荷为引用列表）
                        if (!rag.isEmpty()) {
                            return Flux.concat(Flux.just(RuntimeEvent.retrieval(runId, citationsPayload(rag.hits()))), events);
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
    /**
     * 引用载荷：与知识库问答（{@code KnowledgeQaServiceImpl.hitsPayload}）逐字同构——
     * 同一组字段名与截断长度，前端才能复用同一个解析器与渲染组件。
     */
    private static String citationsPayload(List<RagHit> hits) {
        var payload = hits.stream().map(hit -> {
            Map<String, Object> item = new HashMap<>();
            item.put("knowledgeBaseId", hit.knowledgeBaseId());
            item.put("documentId", hit.documentId());
            item.put("documentTitle", hit.documentTitle());
            item.put("chunkIndex", hit.chunkIndex());
            item.put("content", StrUtil.maxLength(hit.content(), HIT_CONTENT_CAP));
            item.put("score", hit.score());
            return item;
        }).toList();
        return JSONUtil.toJsonStr(payload);
    }

    private static Flux<RuntimeEvent> toRuntimeEvents(String runId, AgentTextEvent event) {
        return switch (event) {
            case AgentTextEvent.Reasoning reasoning ->
                    Flux.just(RuntimeEvent.reasoningDelta(runId, reasoning.text()));
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
