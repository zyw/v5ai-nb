package xin.v5ai.nb.runtime.core;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.*;
import xin.v5ai.nb.common.agentscope.core.service.*;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.model.api.ModelUsageService;
import xin.v5ai.nb.model.api.domain.ModelUsageDTO;
import xin.v5ai.nb.platform.api.AppQuotaService;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 带持久化能力的运行时装饰器：包裹（delegate）另一个 AgentRuntime，
 * 在事件流转过程中把"会话、消息、运行记录、运行事件、Agent 状态"
 * 一并落库，从而支持多轮对话历史与运行审计。
 *
 * 职责边界：delegate 负责真正执行 Agent；本类只做副作用（持久化）。
 * 提供 3 个构造器，持久化能力逐步增强：
 * 1) 最简：会话/事件仓储使用空实现（lambda 空操作），不保存；
 * 2) 中间：显式传入会话与事件仓储；
 * 3) 完整：额外传入 Agent 状态仓储。
 */
@Slf4j
public class PersistingAgentRuntime implements AgentRuntime {
    /** 被装饰的真实运行时（实际执行 Agent） */
    private final AgentRuntime delegate;
    /** 消息仓储：保存用户提问与助手回答 */
    private final MessageService messageService;
    /** 运行记录仓储：记录一次运行的 开始/完成/失败 生命周期 */
    private final RunRecordService runRecordService;
    /** 会话仓储：确保会话记录存在 */
    private final ConversationService conversationService;
    /** 运行事件仓储：保存每一条运行事件 */
    private final RunEventService runEventService;
    /** Agent 状态仓储（可空）：保存会话级 Agent 状态 */
    private final AgentStateService agentStateService;
    /** 用量记录器（可空）：运行完成/失败时记录模型调用用量 */
    private final ModelUsageService usageRecorder;
    /** 应用配额服务（可空）：运行完成后记账（调用次数 + Token） */
    private final AppQuotaService quotaService;

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService
    ) {
        // 最简构造：会话/事件仓储用空实现，不持久化这两类数据
        this(delegate, messageService, runRecordService, conversation -> {}, event -> {}, null);
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService
    ) {
        this(delegate, messageService, runRecordService, conversationService, runEventService, null);
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            AgentStateService agentStateService
    ) {
        this(delegate, messageService, runRecordService, conversationService, runEventService,
                agentStateService, null, null);
    }

    /**
     * 完整构造：附带用量记录与配额记账（Phase 5 可观测性/用量）。
     */
    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            AgentStateService agentStateService,
            ModelUsageService usageRecorder,
           AppQuotaService quotaService
    ) {
        this.delegate = delegate;
        this.messageService = messageService;
        this.runRecordService = runRecordService;
        this.conversationService = conversationService;
        this.runEventService = runEventService;
        this.agentStateService = agentStateService;
        this.usageRecorder = usageRecorder;
        this.quotaService = quotaService;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        // defer：订阅时才执行副作用（落库），避免每次构建 Flux 就写库
        return Flux.defer(() -> {
            // 若未传 conversationId，则先自动生成一个（normalize 负责）
            var normalizedRequest = normalize(request);
            // 1) 登记会话 + 保存用户提问
            conversationService.ensureConversation(new Conversation(normalizedRequest.conversationId(), normalizedRequest.agentKey()));
            messageService.save(new Message(normalizedRequest.conversationId(), normalizedRequest.agentKey(), MessageRole.USER, normalizedRequest.query()));
            // 累积最终回答文本（把 TEXT_DELTA 片段拼起来）
            var answer = new StringBuilder();
            // 用单元素数组持有 runId（lambda 里捕获可变值）
            var runId = new String[1];
            // 用量埋点状态：模型名、开始时间
            var modelName = new String[1];
            var startedAt = new java.time.Instant[1];
            return delegate.stream(normalizedRequest)
                    .doOnNext(event -> {
                        // 外键约束：v5ai_run_event.run_id 引用 v5ai_run.id，
                        // 必须先插入 run 记录再保存其事件
                        if (event.type() == RuntimeEventType.RUN_STARTED) {
                            runId[0] = event.runId();
                            startedAt[0] = event.createdAt();
                            runRecordService.start(event.runId(), normalizedRequest);
                        }
                        if (event.type() == RuntimeEventType.MODEL_CALL) {
                            modelName[0] = event.payload();
                        }
                        // 事件落库；唯一例外是思考增量——它是模型的中间产物、不是回答的一部分，
                        // 落库只会让 v5ai_run_event 随思考长度膨胀，且没有第二个消费方。
                        // 与「知识库问答整体不持久化」同一口径（见 CONTEXT.md「Agent 调试」）。
                        if (event.type() != RuntimeEventType.REASONING_DELTA) {
                            runEventService.save(event);
                        }
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.MESSAGE_COMPLETED) {
                            messageService.save(new Message(normalizedRequest.conversationId(), normalizedRequest.agentKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            messageService.save(new Message(normalizedRequest.conversationId(), normalizedRequest.agentKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                            runRecordService.complete(event.runId());
                            recordUsage(normalizedRequest, runId[0], modelName[0], startedAt[0], answer.toString(), true);
                        }
                        if (event.type() == RuntimeEventType.RUN_FAILED) {
                            runRecordService.fail(event.runId(), event.payload());
                            recordUsage(normalizedRequest, runId[0], modelName[0], startedAt[0], answer.toString(), false);
                        }
                    })
                    // 异常兜底：把异常转成 RUN_FAILED 事件，同样落库并标记 run 失败
                    .onErrorResume(exception -> {
                        boolean alreadyStarted = runId[0] != null;
                        var failedRunId = alreadyStarted ? runId[0] : UUID.randomUUID().toString();
                        if (!alreadyStarted) {
                            // 错误发生在 RUN_STARTED 之前：先建 run 记录，
                            // 才能让随后的失败事件满足外键约束
                            runRecordService.start(failedRunId, normalizedRequest);
                        }
                        // 同步 runId，避免随后的 doOnComplete 重复记账
                        runId[0] = failedRunId;
                        runEventService.save(RuntimeEvent.failed(failedRunId, exception.getMessage()));
                        runRecordService.fail(failedRunId, exception.getMessage());
                        recordUsage(normalizedRequest, failedRunId, modelName[0], startedAt[0], answer.toString(), false);
                        return Flux.just(RuntimeEvent.failed(failedRunId, exception.getMessage()));
                    })
                    // 正常结束但从未收到 RUN_STARTED（delegate 没发启动事件）时，
                    // 也要把回答落库，保证消息完整
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            messageService.save(new Message(normalizedRequest.conversationId(), normalizedRequest.agentKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                            recordUsage(normalizedRequest, null, modelName[0], startedAt[0], answer.toString(), true);
                        }
                    });
        });
    }

    /**
     * 用量埋点：写 v5ai_model_usage 明细 + 递增应用配额记账（Token 按字符数粗略估算）。
     */
    private void recordUsage(AgentRunRequest request, String runId, String modelName,
                             Instant startedAt, String answer, boolean success) {
        if (usageRecorder == null && quotaService == null) {
            return;
        }
        try {
            long promptChars = (request.query() == null ? 0 : request.query().length())
                    + (request.ragContext() == null ? 0 : request.ragContext().length());
            long completionChars = answer == null ? 0 : answer.length();
            long promptTokens = Math.max(1, promptChars / 4);
            long completionTokens = Math.max(1, completionChars / 4);
            long durationMs = startedAt == null ? 0 : Duration.between(startedAt, Instant.now()).toMillis();
            if (usageRecorder != null) {
                usageRecorder.record(new ModelUsageDTO(
                        null, runId, request.agentKey(),
                        modelName == null || modelName.isBlank() ? "unknown" : modelName,
                        promptTokens, completionTokens, Math.max(durationMs, 0),
                        success ? "SUCCESS" : "FAILED", OffsetDateTime.now()));
            }
            if (quotaService != null) {
                quotaService.recordUsage(request.agentKey(), promptTokens + completionTokens);
            }
        } catch (Exception exception) {
            log.warn("failed to record usage for run {}: {}", runId, exception.getMessage());
        }
    }

    @Override
    public Mono<AgentRunResult> call(AgentRunRequest request) {
        // 复用 stream()，过滤出文本增量片段并拼接为最终回答
        return stream(request)
                .filter(event -> event.type() == RuntimeEventType.TEXT_DELTA)
                .map(RuntimeEvent::payload)
                .collectList()
                .map(parts -> new AgentRunResult("", String.join("", parts)));
    }

    /**
     * 保存会话级 Agent 状态（可选能力）：把 agentKey 与最后一次回答写入状态仓储。
     * 未配置 agentStateRepository 时直接跳过。
     */
    private void saveAgentState(AgentRunRequest request, String answer) {
        if (agentStateService == null) {
            return;
        }
        // 序列化为简单的 JSON 字符串，并对回答中的引号做转义
        agentStateService.save(request.conversationId(),
                """
                {"agentKey":"%s","lastAnswer":"%s"}
                """.formatted(request.agentKey(), answer == null ? "" : answer.replace("\"", "\\\"")).trim());
    }

    /**
     * 规范化请求：conversationId 缺失或为空时自动生成一个 UUID，
     * 保证每次运行都能关联到唯一会话。
     */
    private AgentRunRequest normalize(AgentRunRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request;
        }
        return new AgentRunRequest(request.agentKey(), UUID.randomUUID().toString(), request.query(),
                request.ragContext(), request.history(), request.runId());
    }
}
