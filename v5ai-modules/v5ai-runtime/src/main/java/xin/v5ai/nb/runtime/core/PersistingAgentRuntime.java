package xin.v5ai.nb.runtime.core;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.RunUsage;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.core.service.*;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.model.api.ModelUsageService;
import xin.v5ai.nb.model.api.domain.ModelUsageDTO;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.enums.RunStatus;
import xin.v5ai.nb.runtime.core.service.*;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 带持久化能力的运行时装饰器：包裹（delegate）另一个 AgentRuntime，
 * 在事件流转过程中把"会话、消息、运行记录、运行事件、AgentDTO 状态"
 * 一并落库，从而支持多轮对话历史与运行审计。
 *
 * 职责边界：delegate 负责真正执行 AgentDTO；本类只做副作用（持久化）。
 * 提供多个构造器，持久化能力逐步增强：
 * 1) 最简：会话/事件仓储使用空实现（lambda 空操作），不保存；
 * 2) 中间：显式传入会话与事件仓储；
 * 3) 完整：额外传入 AgentDTO 状态仓储、用量记录与取消登记处。
 *
 * <p><b>停止对话</b>：订阅时创建本次运行的取消信号并登记到
 * {@link RunCancellationRegistry}，流上叠加 {@code takeUntilOther}。停止端点触发信号
 * 会取消上游，客户端断流（关页面/断网）则由 Reactor 自己取消——两条路径都走
 * {@link #stream} 里的 {@code doOnCancel}：标记取消、写完成时间、落库已生成的部分回答，
 * 并按已生成内容记用量（否则"点停止"就成了免费额度漏洞）。</p>
 */
@Slf4j
public class PersistingAgentRuntime implements AgentRuntime {
    /** 被装饰的真实运行时（实际执行 AgentDTO） */
    private final AgentRuntime delegate;
    /** 消息仓储：保存用户提问与助手回答 */
    private final MessageService messageService;
    /** 运行记录仓储：记录一次运行的 开始/完成/失败/取消 生命周期 */
    private final RunRecordService runRecordService;
    /** 会话仓储：确保会话存在（含归属断言与归档拦截） */
    private final ConversationService conversationService;
    /** 运行事件仓储：保存每一条运行事件 */
    private final RunEventService runEventService;
    /** AgentDTO 状态仓储（可空）：保存会话级 AgentDTO 状态 */
    private final AgentStateService agentStateService;
    /** 用量记录器（可空）：运行完成/失败/取消时记录模型调用用量 */
    private final ModelUsageService usageRecorder;
    /** 应用配额服务（可空）：运行完成后记账（调用次数 + Token） */
    private final AppQuotaService quotaService;
    /** 进行中运行的取消登记处 */
    private final RunCancellationRegistry cancellationRegistry;

    /**
     * 不落库的会话仓储空实现：供「只要消息与运行记录」的最简构造器使用。
     */
    private static final ConversationService NO_OP_CONVERSATIONS = new ConversationService() {
        @Override
        public void ensureConversation(ConversationDTO conversation) {
        }

        @Override
        public Optional<ConversationDTO> findById(String conversationId) {
            return Optional.empty();
        }

        @Override
        public List<ConversationDTO> list(String agentKey, Long apiKeyId, boolean archived) {
            return List.of();
        }

        @Override
        public boolean rename(String conversationId, String name) {
            return false;
        }

        @Override
        public boolean setArchived(String conversationId, boolean archived) {
            return false;
        }
    };

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService
    ) {
        // 最简构造：会话/事件仓储用空实现，不持久化这两类数据
        this(delegate, messageService, runRecordService, NO_OP_CONVERSATIONS, event -> {
            return false;
        }, null);
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
     * 完整构造：附带用量记录与配额记账（Phase 5 可观测性/用量）、取消登记处（停止对话）。
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
        this(delegate, messageService, runRecordService, conversationService, runEventService,
                agentStateService, usageRecorder, quotaService, new RunCancellationRegistry());
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            AgentStateService agentStateService,
            ModelUsageService usageRecorder,
            AppQuotaService quotaService,
            RunCancellationRegistry cancellationRegistry
    ) {
        this.delegate = delegate;
        this.messageService = messageService;
        this.runRecordService = runRecordService;
        this.conversationService = conversationService;
        this.runEventService = runEventService;
        this.agentStateService = agentStateService;
        this.usageRecorder = usageRecorder;
        this.quotaService = quotaService;
        this.cancellationRegistry = cancellationRegistry == null
                ? new RunCancellationRegistry() : cancellationRegistry;
    }

    @Override
    public Flux<RuntimeRunEventDTO> stream(AgentRunBo request) {
        // defer：订阅时才执行副作用（落库），避免每次构建 Flux 就写库
        return Flux.defer(() -> {
            // 若未传 conversationId，则先自动生成一个（normalize 负责）
            var normalizedRequest = normalize(request);
            // 1) 登记会话（归属断言 + 归档拦截）+ 保存用户提问（含附件）
            conversationService.ensureConversation(new ConversationDTO(
                    normalizedRequest.conversationId(), normalizedRequest.agentKey(),
                    normalizedRequest.apiKeyId(), normalizedRequest.userId(),
                    null, null, null, null));
            // 「重新生成」复用的是既有提问行（提问保持有效、只替换回答），因此不再新插用户消息——
            // 否则历史里会出现两条一模一样的提问。
            var userMessageId = normalizedRequest.reuseUserMessage() ? null
                    : messageService.save(new SessionMessage(normalizedRequest.conversationId(),
                            normalizedRequest.agentKey(), MessageRole.USER, normalizedRequest.query())
                            .withAttachments(normalizedRequest.attachments()));
            // 累积最终回答文本（把 TEXT_DELTA 片段拼起来）
            var answer = new StringBuilder();
            // 用单元素数组持有 runId（lambda 里捕获可变值）
            var runId = new String[1];
            // 用量埋点状态：模型名、开始时间
            var modelName = new String[1];
            var startedAt = new java.time.Instant[1];
            // 回答只落库一次：取消与正常完成可能先后到达，重复写入会产生两条助手消息
            var answerSaved = new AtomicBoolean();
            // 本次运行的取消信号：订阅时创建，收到 RUN_STARTED 后按 runId 登记
            var cancelSignal = Sinks.<Void>empty();
            // 本次运行的用量汇总：RUN_COMPLETED 上挂载、并供记账复用同一个对象
            var usage = new RunUsage[1];
            return delegate.stream(normalizedRequest)
                    // 本轮提问的消息 id 挂到 RUN_STARTED 上：前端不用刷新历史就能「重新生成」刚生成的这一轮
                    .map(event -> attachUserMessageId(event, userMessageId))
                    // 用量必须在副作用（落库 / 推送）之前算好并挂到 RUN_COMPLETED 上：
                    // 这样「前端看到的数字」与「写进 v5ai_model_usage 的数字」是同一份，不会各估一套
                    .map(event -> attachUsage(event, normalizedRequest, startedAt, answer, usage))
                    .doOnNext(event -> {
                        // 外键约束：v5ai_run_event.run_id 引用 v5ai_run.id，
                        // 必须先插入 run 记录再保存其事件
                        if (event.type() == RuntimeEventType.RUN_STARTED) {
                            runId[0] = event.runId();
                            startedAt[0] = event.createdAt();
                            runRecordService.start(event.runId(), normalizedRequest);
                            cancellationRegistry.register(event.runId(), cancelSignal);
                        }
                        if (event.type() == RuntimeEventType.MODEL_CALL) {
                            modelName[0] = event.payload();
                        }
                        // 事件落库；唯一例外是思考增量——它是模型的中间产物、不是回答的一部分，
                        // 落库只会让 v5ai_run_event 随思考长度膨胀，且没有第二个消费方。
                        // 与「知识库问答整体不持久化」同一口径（见 CONTEXT.md「AgentDTO 调试」）。
                        if (event.type() != RuntimeEventType.REASONING_DELTA) {
                            runEventService.save(event);
                        }
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.MESSAGE_COMPLETED) {
                            // 这时还没有用量（它挂在 RUN_COMPLETED 上），落库的用量留空
                            saveAnswerOnce(normalizedRequest, answer, null, answerSaved);
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            saveAnswerOnce(normalizedRequest, answer, usage[0], answerSaved);
                            runRecordService.complete(event.runId());
                            // attachUsage 已经算过：直接复用，保证载荷与账目一致
                            recordUsage(normalizedRequest, runId[0], modelName[0], usage[0],
                                    RunStatus.COMPLETED);
                        }
                        if (event.type() == RuntimeEventType.RUN_FAILED) {
                            runRecordService.fail(event.runId(), event.payload());
                            recordUsage(normalizedRequest, runId[0], modelName[0],
                                    usageOf(normalizedRequest, startedAt[0], answer.toString()),
                                    RunStatus.FAILED);
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
                        runEventService.save(RuntimeRunEventDTO.failed(failedRunId, exception.getMessage()));
                        runRecordService.fail(failedRunId, exception.getMessage());
                        recordUsage(normalizedRequest, failedRunId, modelName[0],
                                usageOf(normalizedRequest, startedAt[0], answer.toString()),
                                RunStatus.FAILED);
                        return Flux.just(RuntimeRunEventDTO.failed(failedRunId, exception.getMessage()));
                    })
                    // 正常结束但从未收到 RUN_STARTED（delegate 没发启动事件）时，
                    // 也要把回答落库，保证消息完整
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            saveAnswerOnce(normalizedRequest, answer,
                                    usageOf(normalizedRequest, startedAt[0], answer.toString()), answerSaved);
                            recordUsage(normalizedRequest, null, modelName[0],
                                    usageOf(normalizedRequest, startedAt[0], answer.toString()),
                                    RunStatus.COMPLETED);
                        }
                    })
                    // 停止/断流：取消是正常收尾，不是异常——保留已生成的部分回答
                    .doOnCancel(() -> finishCanceled(normalizedRequest, runId[0], modelName[0], startedAt[0],
                            answer, answerSaved))
                    // 取消信号触发时取消上游（信号只在本实例登记过才会有值）
                    .takeUntilOther(cancelSignal.asMono())
                    .doFinally(signal -> cancellationRegistry.unregister(runId[0]));
        });
    }

    /**
     * 取消收尾：标记 CANCELED（带 {@code WHERE status='RUNNING'} 条件，不会覆盖已落定的状态）、
     * 落库已生成的部分回答，并按已生成内容记用量。
     */
    private void finishCanceled(AgentRunBo request, String runId, String modelName, Instant startedAt,
                                StringBuilder answer, AtomicBoolean answerSaved) {
        try {
            if (runId != null) {
                runRecordService.cancel(runId);
            }
            saveAnswerOnce(request, answer, usageOf(request, startedAt, answer.toString()), answerSaved);
            recordUsage(request, runId, modelName,
                    usageOf(request, startedAt, answer.toString()), RunStatus.CANCELED);
            log.info("run {} canceled by caller, {} chars of partial answer kept", runId, answer.length());
        } catch (Exception exception) {
            log.warn("failed to finish canceled run {}: {}", runId, exception.getMessage());
        }
    }

    /**
     * 助手回答只落库一次：取消与正常完成可能先后到达（例如先点停止、随后模型又答完），
     * 无保护地写入会产生两条助手消息。
     */
    private void saveAnswerOnce(AgentRunBo request, StringBuilder answer, RunUsage usage,
                                AtomicBoolean answerSaved) {
        if (!answerSaved.compareAndSet(false, true)) {
            return;
        }
        // 用量跟着回答一起落库：刷新页面后读历史仍能看到「用量 / 用时」
        messageService.save(new SessionMessage(request.conversationId(), request.agentKey(),
                MessageRole.ASSISTANT, answer.toString()).withUsage(usage));
        saveAgentState(request, answer.toString());
    }

    /**
     * 按字符数估算用量（与 v5ai_model_usage 记账同一口径）。
     */
    private static RunUsage usageOf(AgentRunBo request, Instant startedAt, String answer) {
        long promptChars = (request.query() == null ? 0 : request.query().length())
                + (request.ragContext() == null ? 0 : request.ragContext().length());
        long completionChars = answer == null ? 0 : answer.length();
        long durationMs = startedAt == null ? 0 : Duration.between(startedAt, Instant.now()).toMillis();
        return RunUsage.of(Math.max(1, promptChars / 4), Math.max(1, completionChars / 4),
                Math.max(durationMs, 0));
    }

    /**
     * 给 RUN_STARTED 挂上本轮用户消息 id（其余事件原样透传），供前端把「重新生成」的锚点记在这一轮上。
     *
     * <p>复用既有提问的运行没有新消息（id 为 null），载荷留空——前端本来就持有那条提问的 id。</p>
     */
    private static RuntimeRunEventDTO attachUserMessageId(RuntimeRunEventDTO event, Long userMessageId) {
        if (event.type() != RuntimeEventType.RUN_STARTED || userMessageId == null) {
            return event;
        }
        return RuntimeRunEventDTO.started(event.runId(),
                JSONUtil.createObj().set("userMessageId", userMessageId).toString());
    }

    /**
     * 给 RUN_COMPLETED 挂上用量载荷（其余事件原样透传），供前端展示「用量 / 用时」。
     *
     * <p>此时 {@code answer} 已经收齐全部 TEXT_DELTA 片段（增量事件在前、完成事件在后），
     * 所以算出来的完成 token 就是整段回答的。</p>
     */
    private static RuntimeRunEventDTO attachUsage(RuntimeRunEventDTO event, AgentRunBo request,
                                                  Instant[] startedAt, StringBuilder answer, RunUsage[] usage) {
        if (event.type() != RuntimeEventType.RUN_COMPLETED) {
            return event;
        }
        var computed = usageOf(request, startedAt[0], answer.toString());
        usage[0] = computed;
        return RuntimeRunEventDTO.completed(event.runId(), computed.toJson());
    }

    /**
     * 用量埋点：写 v5ai_model_usage 明细 + 递增应用配额记账。
     */
    private void recordUsage(AgentRunBo request, String runId, String modelName,
                             RunUsage usage, RunStatus status) {
        if (usageRecorder == null && quotaService == null) {
            return;
        }
        try {
            if (usageRecorder != null) {
                usageRecorder.record(new ModelUsageDTO(
                        null, runId, request.agentKey(),
                        modelName == null || modelName.isBlank() ? "unknown" : modelName,
                        usage.promptTokens(), usage.completionTokens(), usage.durationMs(),
                        status.name(), OffsetDateTime.now()));
            }
            if (quotaService != null) {
                quotaService.recordUsage(request.agentKey(), usage.totalTokens());
            }
        } catch (Exception exception) {
            log.warn("failed to record usage for run {}: {}", runId, exception.getMessage());
        }
    }

    @Override
    public Mono<AgentRunVo> call(AgentRunBo request) {
        // 复用 stream()，过滤出文本增量片段并拼接为最终回答
        return stream(request)
                .filter(event -> event.type() == RuntimeEventType.TEXT_DELTA)
                .map(RuntimeRunEventDTO::payload)
                .collectList()
                .map(parts -> new AgentRunVo("", String.join("", parts)));
    }

    /**
     * 保存会话级 AgentDTO 状态（可选能力）：把 agentKey 与最后一次回答写入状态仓储。
     * 未配置 agentStateRepository 时直接跳过。
     */
    private void saveAgentState(AgentRunBo request, String answer) {
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
    private AgentRunBo normalize(AgentRunBo request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request;
        }
        return request.withConversationId(UUID.randomUUID().toString());
    }
}
