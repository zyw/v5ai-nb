package xin.v5ai.nb.runtime;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.application.PublishedApplicationResolver;

/**
 * AgentScope 运行时：{@link AgentRuntime} 的默认实现。
 *
 * 流程编排：解析已发布应用 → 生成 runId → 可选构建 RAG 上下文 →
 * 委托 AgentTextExecutor 流式执行 → 把内部事件映射为统一 RuntimeEvent。
 * 整个执行是惰性的（Flux.defer），订阅时才真正开始运行。
 */
public class AgentScopeRuntime implements AgentRuntime {
    /** 已发布应用解析器：根据 appKey 找到已发布的应用配置 */
    private final PublishedApplicationResolver applicationResolver;
    /** 底层文本执行器：真正驱动 Agent 跑模型 */
    private final AgentTextExecutor agentTextExecutor;
    /** RAG 上下文构建器（可空，为空则不做知识检索） */
    private final RagContextProvider ragContextProvider;

    /**
     * 无 RAG 的构造器：不配置知识检索能力。
     */
    public AgentScopeRuntime(PublishedApplicationResolver applicationResolver, AgentTextExecutor agentTextExecutor) {
        this(applicationResolver, agentTextExecutor, null);
    }

    /**
     * 全参构造：可附带 RAG 上下文构建器。
     */
    public AgentScopeRuntime(PublishedApplicationResolver applicationResolver, AgentTextExecutor agentTextExecutor,
                             RagContextProvider ragContextProvider) {
        this.applicationResolver = applicationResolver;
        this.agentTextExecutor = agentTextExecutor;
        this.ragContextProvider = ragContextProvider;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        // defer：延迟到订阅时才解析应用、生成 runId，避免每次构建都执行副作用
        return Flux.defer(() -> {
            var application = applicationResolver.resolve(request.appKey());
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
                                ? request
                                : request.withRagContext(context);
                        // 将内部事件统一映射为平台 RuntimeEvent
                        var events = agentTextExecutor.streamText(application, enriched)
                                .map(event -> switch (event) {
                                    case AgentTextEvent.Text text -> RuntimeEvent.textDelta(runId, text.text());
                                    case AgentTextEvent.ModelCall call -> RuntimeEvent.modelCall(runId, call.modelName());
                                });
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
}
