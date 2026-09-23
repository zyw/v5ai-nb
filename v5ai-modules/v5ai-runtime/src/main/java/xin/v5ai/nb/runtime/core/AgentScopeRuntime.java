package xin.v5ai.nb.runtime.core;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.RagContextProvider;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.rag.CitationPayload;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.runtime.core.domain.ConversationSummaryDTO;
import xin.v5ai.nb.common.agentscope.core.history.HistoryWindow;
import xin.v5ai.nb.common.agentscope.core.history.HistoryWindowResult;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.resolver.ModelToolSupportResolver;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.runtime.core.service.MessageService;

import java.util.*;
import java.util.function.Function;

/**
 * AgentScope 运行时：{@link AgentRuntime} 的默认实现。
 *
 * 流程编排：解析 AgentDTO（正式路径仅已发布；也可注入调试解析器允许草稿态）→ 生成 runId →
 * 可选构建 RAG 上下文 → 委托 AgentTextExecutor 流式执行 → 把内部事件映射为统一 RuntimeRunEventDTO。
 * 整个执行是惰性的（Flux.defer），订阅时才真正开始运行。
 */
@Slf4j
public class AgentScopeRuntime implements AgentRuntime {

    /** AgentDTO 解析器：根据 agentKey 得到 AgentDTO 配置（正式路径要求已发布，调试路径允许草稿） */
    private final Function<String, AgentDTO> agentResolver;
    /** 底层文本执行器：真正驱动 AgentDTO 跑模型 */
    private final AgentTextExecutor agentTextExecutor;
    /** RAG 上下文构建器（可空，为空或 AgentDTO 关闭 RAG 时不做知识检索） */
    private final RagContextProvider ragContextProvider;
    /** 消息仓储（可空）：AgentDTO 开启记忆时读取会话历史注入上下文 */
    private final MessageService messageService;
    /** 模型工具调用能力判定（可空）：智能调用模式依赖模型支持 function calling，不支持时回退强制调用 */
    private final ModelToolSupportResolver modelToolSupportResolver;
    /** 同会话历史的窗口预算：决定本次最多把多少历史发给模型（未配置时为不裁剪） */
    private final HistoryWindow historyWindow;
    /** 会话摘要（可空）：历史窗口之外的旧内容压缩成的一段文本，随系统提示一起注入 */
    private final ConversationSummaryService summaryService;

    /**
     * 无 RAG 的构造器：不配置知识检索能力；仅解析已发布 AgentDTO。
     */
    public AgentScopeRuntime(PublishedAgentResolver agentResolver, AgentTextExecutor agentTextExecutor) {
        this(agentResolver::resolve, agentTextExecutor, null, null);
    }

    /**
     * 全参构造：可附带 RAG 上下文构建器；仅解析已发布 AgentDTO。
     */
    public AgentScopeRuntime(PublishedAgentResolver agentResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider) {
        this(agentResolver::resolve, agentTextExecutor, ragContextProvider, null);
    }

    /**
     * 自定义解析器构造：支持传入调试解析器（如允许草稿态的 {@code resolveForDebug}）。
     */
    public AgentScopeRuntime(Function<String, AgentDTO> agentResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider) {
        this(agentResolver, agentTextExecutor, ragContextProvider, null);
    }

    /**
     * 全参构造：附带消息仓储与模型工具能力判定；AgentDTO 开启记忆（memoryEnabled）时按会话加载历史，
     * 智能调用模式（rag_call_mode=SMART）依赖模型工具能力判定来决定是否回退强制调用。
     */
    public AgentScopeRuntime(Function<String, AgentDTO> agentResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider, MessageService messageService) {
        this(agentResolver, agentTextExecutor, ragContextProvider, messageService, null);
    }

    /**
     * 完整构造：在消息仓储之外附带模型工具能力判定（可空，空则智能调用默认视为模型支持工具）。
     */
    public AgentScopeRuntime(Function<String, AgentDTO> agentResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider, MessageService messageService,
                             ModelToolSupportResolver modelToolSupportResolver) {
        this(agentResolver, agentTextExecutor, ragContextProvider, messageService, modelToolSupportResolver,
                HistoryWindow.unlimited());
    }

    /**
     * 全参构造：额外给定历史窗口预算（{@code v5ai.agentscope.history}）。
     *
     * @param historyWindow 历史窗口；{@link HistoryWindow#unlimited()} 表示不裁剪
     */
    public AgentScopeRuntime(Function<String, AgentDTO> agentResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider, MessageService messageService,
                             ModelToolSupportResolver modelToolSupportResolver, HistoryWindow historyWindow) {
        this(agentResolver, agentTextExecutor, ragContextProvider, messageService, modelToolSupportResolver,
                historyWindow, null);
    }

    /**
     * 全参构造：再附带会话摘要仓储（可空，空则不注入摘要）。
     *
     * @param summaryService 会话摘要端口；{@code null} 表示本次运行不注入历史摘要
     */
    public AgentScopeRuntime(Function<String, AgentDTO> agentResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider, MessageService messageService,
                             ModelToolSupportResolver modelToolSupportResolver, HistoryWindow historyWindow,
                             ConversationSummaryService summaryService) {
        this.agentResolver = agentResolver;
        this.agentTextExecutor = agentTextExecutor;
        this.ragContextProvider = ragContextProvider;
        this.messageService = messageService;
        this.modelToolSupportResolver = modelToolSupportResolver;
        this.historyWindow = historyWindow == null ? HistoryWindow.unlimited() : historyWindow;
        this.summaryService = summaryService;
    }

    @Override
    public Flux<RuntimeRunEventDTO> stream(AgentRunBo request) {
        // defer：延迟到订阅时才解析应用、生成 runId，避免每次构建都执行副作用
        return Flux.defer(() -> {
            var agent = agentResolver.apply(request.agentKey());
            // 记忆开关：仅当 memoryEnabled 时按会话加载历史；加载到的历史（含 resume 传入的）
            // 一律过历史窗口，再决定发给模型多少
            var prepared = withMemory(agent, request);
            var req = prepared.request();
            // 窗口统计随 RUN_STARTED 一起发出：前端/审计能看到本轮实际带了多少历史、裁掉了多少
            var startedPayload = contextPayload(prepared.window());
            var runId = UUID.randomUUID().toString();
            // 强制调用（FORCED，或智能调用因模型不支持工具而回退）→ pre-step 检索并注入；
            // 智能调用（SMART）→ 跳过 pre-step，交由执行器注册 rag_search 工具按需检索
            var contextMono = (ragContextProvider == null || !shouldPreRetrieve(agent))
                    ? Mono.just(RagContext.empty())
                    : ragContextProvider.buildContext(agent, req);
            return Flux.concat(
                    // 1) 运行开始事件（载荷含本轮上下文统计）
                    Flux.just(RuntimeRunEventDTO.started(runId, startedPayload)),
                    // 2) 拿到检索结果后执行 AgentDTO
                    contextMono.flatMapMany(rag -> {
                        // 有有效上下文则拼进请求，否则用原请求
                        var enriched = rag.isEmpty()
                                ? req.withRunId(runId)
                                : req.withRagContext(rag.context()).withRunId(runId);
                        // 将内部事件统一映射为平台 RuntimeRunEventDTO（工具事件可展开为多个事件）
                        var events = agentTextExecutor.streamText(agent, enriched)
                                .flatMap(event -> toRuntimeEvents(runId, event));
                        // 有检索上下文时，在 AgentDTO 输出之前先发一个 RETRIEVAL 事件（载荷为引用列表）
                        if (!rag.isEmpty()) {
                            return Flux.concat(Flux.just(RuntimeRunEventDTO.retrieval(runId, CitationPayload.toJson(rag.hits()))), events);
                        }
                        return events;
                    }),
                    // 3) 运行结束事件
                    Flux.just(RuntimeRunEventDTO.completed(runId)));
        });
    }

    @Override
    public Mono<AgentRunVo> call(AgentRunBo request) {
        // 复用 stream()，只保留文本增量片段并拼接为完整回答；
        // 注意：这里 runId 与 stream 事件流里的不同，是 call 自己生成的
        return stream(request)
                .filter(event -> event.type() == RuntimeEventType.TEXT_DELTA)
                .map(RuntimeRunEventDTO::payload)
                .collectList()
                .map(parts -> new AgentRunVo(UUID.randomUUID().toString(), String.join("", parts)));
    }

    /**
     * 记忆准备：按会话加载历史 → 过历史窗口 → 得到本次真正发给模型的那一段。
     *
     * <p>加载只发生在请求本身没带历史时；{@code resume} 的历史由 {@code ChatController} 传入，
     * 但**窗口裁剪对两条路一视同仁**——否则 resume 就成了绕过预算的后门。</p>
     *
     * <p>加载发生在 {@link PersistingAgentRuntime} 已落库当前用户消息之后，故要剔除尾部那条
     * 用户消息（就是我们自己），避免被重复回放。</p>
     */
    private MemoryPreparation withMemory(AgentDTO agent, AgentRunBo request) {
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            return new MemoryPreparation(request, null);
        }
        var history = new ArrayList<>(request.history());
        if (history.isEmpty()) {
            if (!agent.memoryEnabled() || messageService == null) {
                return new MemoryPreparation(request, null);
            }
            // SQL 侧按窗口取最近若干条：长会话不必每次把整张消息表读出来再在内存里裁
            history = new ArrayList<>(messageService.findRecentByConversationId(
                    request.conversationId(), historyWindow.fetchLimit()));
            if (!history.isEmpty() && history.get(history.size() - 1).role() == MessageRole.USER) {
                // 只按角色判定，不再比对文本：多模态下「相同文字、不同图片」的两条提问靠文本无法区分，
                // 继续比对 content 会把本来该保留的历史误删（或在本条带图时判不出来而重复回放）。
                history.remove(history.size() - 1);
            }
        }
        if (history.isEmpty()) {
            return new MemoryPreparation(request, null);
        }
        var window = historyWindow.select(history);
        return new MemoryPreparation(
                request.withHistory(window.messages()).withSummaryContext(loadSummary(agent, request)), window);
    }

    /**
     * 会话摘要：只在开启记忆时携带——它就是「本会话更早的历史」，关掉记忆就不该有它的影子。
     *
     * @return 摘要正文；没有摘要或摘要服务不可用时返回 {@code null}
     */
    private String loadSummary(AgentDTO agent, AgentRunBo request) {
        if (!agent.memoryEnabled() || summaryService == null) {
            return null;
        }
        return summaryService.find(request.conversationId())
                .map(ConversationSummaryDTO::summary)
                .orElse(null);
    }

    /**
     * RUN_STARTED 载荷里的上下文段；未参与记忆（开关关闭、没有历史）时返回空串，
     * 载荷形态与改造前完全一致。
     */
    private static String contextPayload(HistoryWindowResult window) {
        if (window == null) {
            return "";
        }
        return JSONUtil.createObj()
                .set("context", JSONUtil.createObj()
                        .set("keptMessages", window.keptMessages())
                        .set("droppedMessages", window.droppedMessages())
                        .set("droppedChars", window.droppedChars())
                        .set("keptChars", window.keptChars())
                        .set("trimmed", window.trimmed()))
                .toString();
    }

    /**
     * 记忆准备结果：换好历史的请求 + 可观测的窗口统计（未参与记忆时为 {@code null}）。
     */
    private record MemoryPreparation(AgentRunBo request, HistoryWindowResult window) {
    }

    /**
     * 判断本次运行是否需 pre-step 检索（强制调用）：{@code ragEnabled=false} 或
     * SMART（模型支持工具）时为 false；FORCED、或 SMART 但模型不支持工具调用时回退为 true。
     */
    private boolean shouldPreRetrieve(AgentDTO agent) {
        if (!agent.ragEnabled()) {
            return false;
        }
        // 未知/非法取值按 from() 归一为强制调用，与 ModelStreamTextExecutor.isSmartRag 的判定保持一致
        if (RagCallMode.from(agent.ragCallMode()) == RagCallMode.FORCED) {
            return true;
        }
        // SMART：模型不支持工具调用时，注册 rag_search 也无法被模型调用，回退为强制调用
        Long modelId = agent.modelId();
        if (modelId == null) {
            return true;
        }
        if (modelToolSupportResolver != null && !modelToolSupportResolver.supportsToolCall(modelId)) {
            log.warn("agent '{}' 绑定模型 {} 不支持工具调用，rag_call_mode=SMART 回退为强制调用",
                    agent.agentKey(), modelId);
            return true;
        }
        return false;
    }

    /**
     * 把执行器内部事件映射为平台事件流。
     */
    private static Flux<RuntimeRunEventDTO> toRuntimeEvents(String runId, AgentTextEvent event) {
        return switch (event) {
            case AgentTextEvent.Reasoning reasoning ->
                    Flux.just(RuntimeRunEventDTO.reasoningDelta(runId, reasoning.text()));
            case AgentTextEvent.Text text -> Flux.just(RuntimeRunEventDTO.textDelta(runId, text.text()));
            case AgentTextEvent.ModelCall call -> call.modelId() == null
                    ? Flux.just(RuntimeRunEventDTO.modelCall(runId, call.modelName()))
                    : Flux.just(RuntimeRunEventDTO.modelCall(runId, call.modelId(), call.modelKey(), call.modelName()));
            case AgentTextEvent.ToolCall call -> {
                var callEvent = Flux.just(RuntimeRunEventDTO.toolCall(runId, toolCallPayload(call)));
                // APPROVE 决策：先发出 PERMISSION_REQUIRED 事件（人工审批提示），随后自动放行
                if (call.permission() == McpToolPermission.APPROVE) {
                    yield Flux.concat(callEvent, Flux.just(RuntimeRunEventDTO.permissionRequired(runId,
                            "tool '" + call.toolName() + "' requires approval (auto-approved)")));
                }
                yield callEvent;
            }
            case AgentTextEvent.ToolResult result -> Flux.just(RuntimeRunEventDTO.toolResult(runId,
                    result.success() ? result.toolName() : result.toolName() + " failed: " + result.message()));
            case AgentTextEvent.Retrieval retrieval ->
                    Flux.just(RuntimeRunEventDTO.retrieval(runId, CitationPayload.toJson(retrieval.hits())));
            // 用量是平台内部事件：交给持久化侧记账，既不落库也不推给客户端
            case AgentTextEvent.Usage usage ->
                    Flux.just(RuntimeRunEventDTO.modelUsage(runId, usage.inputTokens(), usage.outputTokens(),
                            usage.estimated()));
        };
    }

    private static String toolCallPayload(AgentTextEvent.ToolCall call) {
        return "{\"tool\":\"" + call.toolName() + "\",\"permission\":\"" + call.permission() + "\"}";
    }
}
