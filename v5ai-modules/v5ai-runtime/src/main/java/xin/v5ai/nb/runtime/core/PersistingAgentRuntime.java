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
import xin.v5ai.nb.common.agentscope.core.domain.rag.CitationPayload;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.core.token.PromptTokenEstimator;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.model.api.ModelUsageService;
import xin.v5ai.nb.model.api.domain.ModelUsageDTO;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.enums.RunStatus;
import xin.v5ai.nb.runtime.core.service.*;
import xin.v5ai.nb.runtime.core.utils.ConversationNaming;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    /** 首轮结束后的标题改写（可空）：只在本次新建了会话时触发一次 */
    private final ConversationTitleGenerator titleGenerator;
    /** 回答落库后的会话摘要（可空）：把历史窗口之外的旧内容滚动压缩成一段文本 */
    private final ConversationSummaryGenerator summaryGenerator;
    /**
     * 是否把思考过程随回答落库（{@code v5ai.agentscope.reasoning-persist-enabled}）。
     *
     * <p>关闭时思考照常流式推给客户端，只是不再持久化——给需要规避敏感内容留的开关。</p>
     */
    private final boolean persistReasoning;

    /**
     * 不落库的会话仓储空实现：供「只要消息与运行记录」的最简构造器使用。
     */
    private static final ConversationService NO_OP_CONVERSATIONS = new ConversationService() {
        @Override
        public boolean ensureConversation(ConversationDTO conversation) {
            // 不落库的运行时没有「新建会话」可言，也就没有标题改写
            return false;
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
        public boolean renameIfAutoNamed(String conversationId, String name) {
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
        this(delegate, messageService, runRecordService, conversationService, runEventService,
                agentStateService, usageRecorder, quotaService, cancellationRegistry, null);
    }

    /**
     * 完整构造 + 标题改写：首轮结束后用模型把兜底会话名换成短标题。
     *
     * @param titleGenerator 标题改写端口；为 {@code null} 表示只保留兜底名
     */
    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            AgentStateService agentStateService,
            ModelUsageService usageRecorder,
            AppQuotaService quotaService,
            RunCancellationRegistry cancellationRegistry,
            ConversationTitleGenerator titleGenerator
    ) {
        this(delegate, messageService, runRecordService, conversationService, runEventService,
                agentStateService, usageRecorder, quotaService, cancellationRegistry, titleGenerator, null);
    }

    /**
     * 完整构造 + 标题改写 + 会话摘要：两个后台增强都只在回答落库之后触发一次。
     *
     * @param summaryGenerator 会话摘要端口；为 {@code null} 表示不做摘要
     */
    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            AgentStateService agentStateService,
            ModelUsageService usageRecorder,
            AppQuotaService quotaService,
            RunCancellationRegistry cancellationRegistry,
            ConversationTitleGenerator titleGenerator,
            ConversationSummaryGenerator summaryGenerator
    ) {
        this(delegate, messageService, runRecordService, conversationService, runEventService,
                agentStateService, usageRecorder, quotaService, cancellationRegistry, titleGenerator,
                summaryGenerator, true);
    }

    /**
     * 全参构造：额外给定「思考是否随回答落库」。
     *
     * @param persistReasoning {@code false} 时思考照常流式推送，只是不落库
     */
    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageService messageService,
            RunRecordService runRecordService,
            ConversationService conversationService,
            RunEventService runEventService,
            AgentStateService agentStateService,
            ModelUsageService usageRecorder,
            AppQuotaService quotaService,
            RunCancellationRegistry cancellationRegistry,
            ConversationTitleGenerator titleGenerator,
            ConversationSummaryGenerator summaryGenerator,
            boolean persistReasoning
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
        this.titleGenerator = titleGenerator;
        this.summaryGenerator = summaryGenerator;
        this.persistReasoning = persistReasoning;
    }

    @Override
    public Flux<RuntimeRunEventDTO> stream(AgentRunBo request) {
        // defer：订阅时才执行副作用（落库），避免每次构建 Flux 就写库
        return Flux.defer(() -> {
            // 若未传 conversationId，则先自动生成一个（normalize 负责）
            var normalizedRequest = normalize(request);
            // 1) 登记会话（归属断言 + 归档拦截）+ 保存用户提问（含附件）。
            // 名称在这里就写好（首条提问的兜底名），门户列表因此只读会话表，不必再查消息表取预览；
            // 返回值告诉后面「这是不是本次新建的会话」——只有首轮才值得再花一次模型调用改标题。
            var firstTurn = conversationService.ensureConversation(new ConversationDTO(
                    normalizedRequest.conversationId(), normalizedRequest.agentKey(),
                    normalizedRequest.apiKeyId(), normalizedRequest.userId(),
                    ConversationNaming.fromQuery(normalizedRequest.query()), null, null, null));
            // 「重新生成」复用的是既有提问行（提问保持有效、只替换回答），因此不再新插用户消息——
            // 否则历史里会出现两条一模一样的提问。
            var userMessageId = normalizedRequest.reuseUserMessage() ? null
                    : messageService.save(new SessionMessage(normalizedRequest.conversationId(),
                            normalizedRequest.agentKey(), MessageRole.USER, normalizedRequest.query())
                            .withAttachments(normalizedRequest.attachments()));
            // 累积最终回答文本（把 TEXT_DELTA 片段拼起来）
            var answer = new StringBuilder();
            // 累积思考过程（REASONING_DELTA 片段）：随回答一起落库，刷新后仍能回看；
            // 注意它**不回放给模型**——记忆窗口与摘要的取数都不带这一列（见 docs/adr/0007）
            var reasoning = new StringBuilder();
            // 累积本轮引用（RETRIEVAL 事件载荷）：一轮里智能调用可能检索多次，随回答一起落库，
            // 刷新或重进会话后门户仍能回看当时引用的切片（见 docs/adr/0009）
            var citations = new ArrayList<RagHit>();
            // 用单元素数组持有 runId（lambda 里捕获可变值）
            var runId = new String[1];
            // 用量埋点状态：模型 ID/模型名、开始时间
            var modelId = new Long[1];
            var modelName = new String[1];
            var startedAt = new java.time.Instant[1];
            // 执行器回报的用量（真实值优先、估算兜底）：见 AgentTextEvent.Usage
            var reportedUsage = new ReportedUsage();
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
                    .map(event -> attachUsage(event, normalizedRequest, startedAt, answer, usage, reportedUsage))
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
                            var call = parseModelCall(event.payload());
                            modelId[0] = call.modelId();
                            modelName[0] = call.modelKey();
                        }
                        // 引用累积：一次运行可能发多条 RETRIEVAL（智能调用下每次 rag_search 之后一条），
                        // 累加去重后随回答落库——历史回看与当时实时所见一致
                        if (event.type() == RuntimeEventType.RETRIEVAL) {
                            accumulateCitations(citations, event.payload());
                        }
                        // 用量是内部事件：只累加，不落库、不外发（下面统一过滤掉）
                        if (event.type() == RuntimeEventType.MODEL_USAGE) {
                            reportedUsage.accept(event);
                        }
                        // 事件落库：思考增量与用量都不落 v5ai_run_event——
                        // 前者会让事件表随思考长度膨胀（它改落在所属助手消息上，见 messages.reasoning），
                        // 后者统一走助手消息 + v5ai_model_usage，事件表里没有第二个消费方。
                        // 与「知识库问答整体不持久化」同一口径（见 CONTEXT.md「AgentDTO 调试」）。
                        if (event.type() != RuntimeEventType.REASONING_DELTA
                                && event.type() != RuntimeEventType.MODEL_USAGE) {
                            runEventService.save(event);
                        }
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.REASONING_DELTA) {
                            reasoning.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.MESSAGE_COMPLETED) {
                            // 这时还没有用量（它挂在 RUN_COMPLETED 上），落库的用量留空
                            saveAnswerOnce(normalizedRequest, answer, reasoning, citations, null, answerSaved,
                                    firstTurn);
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            saveAnswerOnce(normalizedRequest, answer, reasoning, citations, usage[0],
                                    answerSaved, firstTurn);
                            runRecordService.complete(event.runId());
                            // attachUsage 已经算过：直接复用，保证载荷与账目一致
                            recordUsage(normalizedRequest, runId[0], modelId[0], modelName[0], usage[0],
                                    RunStatus.COMPLETED);
                        }
                        if (event.type() == RuntimeEventType.RUN_FAILED) {
                            runRecordService.fail(event.runId(), event.payload());
                            recordUsage(normalizedRequest, runId[0], modelId[0], modelName[0],
                                    usageOf(normalizedRequest, startedAt[0], answer.toString(), reportedUsage),
                                    RunStatus.FAILED);
                        }
                    })
                    // 用量消费完就从流里摘掉：ChatController 把整条流映射成 SSE，
                    // 不摘掉就会把内部事件推给客户端（与「不落库、不推前端」的约定冲突）
                    .filter(event -> event.type() != RuntimeEventType.MODEL_USAGE)
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
                        recordUsage(normalizedRequest, failedRunId, modelId[0], modelName[0],
                                usageOf(normalizedRequest, startedAt[0], answer.toString(), reportedUsage),
                                RunStatus.FAILED);
                        return Flux.just(RuntimeRunEventDTO.failed(failedRunId, exception.getMessage()));
                    })
                    // 正常结束但从未收到 RUN_STARTED（delegate 没发启动事件）时，
                    // 也要把回答落库，保证消息完整
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            saveAnswerOnce(normalizedRequest, answer, reasoning, citations,
                                    usageOf(normalizedRequest, startedAt[0], answer.toString(), reportedUsage),
                                    answerSaved, firstTurn);
                            recordUsage(normalizedRequest, null, modelId[0], modelName[0],
                                    usageOf(normalizedRequest, startedAt[0], answer.toString(), reportedUsage),
                                    RunStatus.COMPLETED);
                        }
                    })
                    // 停止/断流：取消是正常收尾，不是异常——保留已生成的部分回答
                    .doOnCancel(() -> finishCanceled(normalizedRequest, runId[0], modelId[0], modelName[0], startedAt[0],
                            answer, reasoning, citations, answerSaved, firstTurn, reportedUsage))
                    // 取消信号触发时取消上游（信号只在本实例登记过才会有值）
                    .takeUntilOther(cancelSignal.asMono())
                    .doFinally(signal -> cancellationRegistry.unregister(runId[0]));
        });
    }

    /**
     * 取消收尾：标记 CANCELED（带 {@code WHERE status='RUNNING'} 条件，不会覆盖已落定的状态）、
     * 落库已生成的部分回答，并按已生成内容记用量。
     */
    private void finishCanceled(AgentRunBo request, String runId, Long modelId, String modelName, Instant startedAt,
                                StringBuilder answer, StringBuilder reasoning, List<RagHit> citations,
                                AtomicBoolean answerSaved, boolean firstTurn, ReportedUsage reportedUsage) {
        try {
            if (runId != null) {
                runRecordService.cancel(runId);
            }
            // 被停止时连思考与已发生的引用也一并保留：与「半截回答照常落库」同一口径
            saveAnswerOnce(request, answer, reasoning, citations,
                    usageOf(request, startedAt, answer.toString(), reportedUsage), answerSaved, firstTurn);
            recordUsage(request, runId, modelId, modelName,
                    usageOf(request, startedAt, answer.toString(), reportedUsage), RunStatus.CANCELED);
            log.info("run {} canceled by caller, {} chars of partial answer kept", runId, answer.length());
        } catch (Exception exception) {
            log.warn("failed to finish canceled run {}: {}", runId, exception.getMessage());
        }
    }

    /**
     * 累积本轮引用：一次运行可能发多条 {@code RETRIEVAL}（智能调用下每次 {@code rag_search}
     * 之后追加一条），同一切片按 {@code (knowledgeBaseId, documentId, chunkIndex)} 只保留
     * **首次命中**——顺序即模型实际看到的顺序，相似度也取首次那一次（见 docs/adr/0009）。
     *
     * <p>载荷在事件流里是 JSON 文本，这里解析回结构化命中；损坏的载荷解析为空列表，
     * 绝不影响对话主链路。</p>
     */
    private static void accumulateCitations(List<RagHit> accumulator, String payload) {
        for (RagHit hit : CitationPayload.parse(payload)) {
            if (accumulator.stream().noneMatch(existing -> sameSlice(existing, hit))) {
                accumulator.add(hit);
            }
        }
    }

    /** 同一切片的判定：知识库 + 文档 + 文档内序号（引用载荷里没有切片的向量标识，见 docs/adr/0009）。 */
    private static boolean sameSlice(RagHit left, RagHit right) {
        return Objects.equals(left.knowledgeBaseId(), right.knowledgeBaseId())
                && Objects.equals(left.documentId(), right.documentId())
                && Objects.equals(left.chunkIndex(), right.chunkIndex());
    }

    /**
     * 助手回答只落库一次：取消与正常完成可能先后到达（例如先点停止、随后模型又答完），
     * 无保护地写入会产生两条助手消息。
     *
     * @param firstTurn 本次运行是否创建了会话：只有首轮才改写标题（后续追问不该改会话名）
     */
    private void saveAnswerOnce(AgentRunBo request, StringBuilder answer, StringBuilder reasoning,
                                List<RagHit> citations, RunUsage usage, AtomicBoolean answerSaved,
                                boolean firstTurn) {
        if (!answerSaved.compareAndSet(false, true)) {
            return;
        }
        // 用量、思考与引用跟着回答一起落库：刷新页面后读历史仍能看到「用量 / 用时」、思考过程
        // 与当时引用的切片（思考受 reasoning-persist-enabled 门控；空白思考会被 SessionMessage
        // 归一化为 null；无引用时 metadata 列写 null = 这一轮没有引用）
        messageService.save(new SessionMessage(request.conversationId(), request.agentKey(),
                        MessageRole.ASSISTANT, answer.toString())
                .withReasoning(persistReasoning ? reasoning.toString() : null)
                .withCitations(citations)
                .withUsage(usage));
        saveAgentState(request, answer.toString());
        generateTitleOnce(request, firstTurn);
        summarizeOnce(request);
    }

    /**
     * 首轮收尾时排一次标题改写（异步、失败静默）。
     *
     * <p>放在回答落库之后、且由 {@code answerSaved} 的 CAS 保证只走一次：停止对话（取消路径）
     * 同样算「首轮有结果」，那时也该给会话起个名字。</p>
     */
    private void generateTitleOnce(AgentRunBo request, boolean firstTurn) {
        if (!firstTurn || titleGenerator == null) {
            return;
        }
        try {
            titleGenerator.rewriteIfAutoNamed(request.conversationId(), request.agentKey(), request.query());
        } catch (Exception exception) {
            // 标题是锦上添花：兜底名已经在库里，这里绝不把异常带上对话主链路
            log.debug("failed to schedule conversation title for {}: {}",
                    request.conversationId(), exception.getMessage());
        }
    }

    /**
     * 本次运行的用量：**模型回报的真实值优先**，没有回报时依次退回执行器的估算、平台字符估算
     * （与 {@code v5ai_model_usage} 记账、助手消息落库、{@code RUN_COMPLETED} 载荷共用这一份数字）。
     *
     * <p>真实值口径下的输入 token 是「本次运行全部模型调用的提示词之和」——工具循环会调用多次，
     * 每次都把完整上下文再发一遍，这正是真实成本。</p>
     */
    private static RunUsage usageOf(AgentRunBo request, Instant startedAt, String answer,
                                    ReportedUsage reportedUsage) {
        long promptTokens;
        long completionTokens;
        if (reportedUsage != null && reportedUsage.real) {
            promptTokens = reportedUsage.inputTokens;
            completionTokens = reportedUsage.outputTokens;
        } else {
            long estimatedPrompt = reportedUsage != null && reportedUsage.estimatedSeen
                    ? reportedUsage.estimatedInputTokens
                    : PromptTokenEstimator.estimateRequest(request, PromptTokenEstimator.DEFAULT_IMAGE_TOKENS);
            promptTokens = Math.max(1, estimatedPrompt);
            completionTokens = Math.max(1, PromptTokenEstimator.estimateText(answer));
        }
        long durationMs = startedAt == null ? 0 : Duration.between(startedAt, Instant.now()).toMillis();
        return RunUsage.of(Math.max(promptTokens, 0), Math.max(completionTokens, 0), Math.max(durationMs, 0));
    }

    /**
     * 执行器回报的用量累积器：真实值累加、估算值只留最后一次，真实值一旦出现就永远优先。
     *
     * <p>载荷由 {@code RuntimeRunEventDTO.modelUsage} 产出（JSON），这里只解析两个数字与一个标志位——
     * 事件是平台内部约定，不值得为它再引入一个 DTO 类。</p>
     */
    private static final class ReportedUsage {
        /** 真实输入 token（多轮调用累加） */
        private long inputTokens;
        /** 真实输出 token（多轮调用累加） */
        private long outputTokens;
        /** 执行器给出的估算输入 token（真实值缺失时的兜底） */
        private long estimatedInputTokens;
        /** 是否收到过真实值 */
        private boolean real;
        /** 是否收到过估算值 */
        private boolean estimatedSeen;

        void accept(RuntimeRunEventDTO event) {
            try {
                var payload = JSONUtil.parseObj(event.payload());
                if (payload.getBool("estimated", false)) {
                    estimatedInputTokens = payload.getLong("inputTokens", 0L);
                    estimatedSeen = true;
                } else {
                    inputTokens += payload.getLong("inputTokens", 0L);
                    outputTokens += payload.getLong("outputTokens", 0L);
                    real = true;
                }
            } catch (Exception exception) {
                log.debug("failed to parse MODEL_USAGE payload: {}", exception.getMessage());
            }
        }
    }

    /**
     * 回答落库后请求一次会话摘要（异步、失败静默、可关闭）。
     *
     * <p>触发点与标题改写同一处、同样受 {@code answerSaved} 的 CAS 保护——取消路径也算
     * 「这一轮有结果」，那时同样该把窗口外的新内容压进摘要。</p>
     */
    private void summarizeOnce(AgentRunBo request) {
        if (summaryGenerator == null) {
            return;
        }
        try {
            summaryGenerator.summarizeIfNeeded(request.conversationId(), request.agentKey());
        } catch (Exception exception) {
            // 摘要是锦上添花：绝不能把异常带上对话主链路
            log.debug("failed to schedule conversation summary for {}: {}",
                    request.conversationId(), exception.getMessage());
        }
    }

    /**
     * 给 RUN_STARTED 挂上本轮用户消息 id（其余事件原样透传），供前端把「重新生成」的锚点记在这一轮上。
     *
     * <p><b>必须写成字符串。</b>消息主键是雪花号（19 位），超出 JS 的 Number.MAX_SAFE_INTEGER：
     * 数字形态过一趟浏览器就会被四舍五入（{@code …417794} → {@code …417800}），前端照着
     * 「重新生成」回传时服务端查无此消息，表现为 404「提问不存在」。Jackson 那条路（历史接口的
     * {@code messageId}）由 {@code BigNumberSerializer} 自动输出字符串，而本载荷是 Hutool 手拼的，
     * 绕过了那个序列化器，只能在这里显式转字符串，两条路才对得上。</p>
     *
     * <p>复用既有提问的运行没有新消息（id 为 null），载荷留空——前端本来就持有那条提问的 id。</p>
     */
    private static RuntimeRunEventDTO attachUserMessageId(RuntimeRunEventDTO event, Long userMessageId) {
        if (event.type() != RuntimeEventType.RUN_STARTED || userMessageId == null) {
            return event;
        }
        // 载荷里可能已经有别的字段（如本轮上下文窗口统计），只能补字段、不能整体替换
        var payload = event.payload() == null || event.payload().isBlank()
                ? JSONUtil.createObj()
                : JSONUtil.parseObj(event.payload());
        payload.set("userMessageId", String.valueOf(userMessageId));
        return RuntimeRunEventDTO.started(event.runId(), payload.toString());
    }

    /**
     * 给 RUN_COMPLETED 挂上用量载荷（其余事件原样透传），供前端展示「用量 / 用时」。
     *
     * <p>此时 {@code answer} 已经收齐全部 TEXT_DELTA 片段（增量事件在前、完成事件在后），
     * 所以算出来的完成 token 就是整段回答的。</p>
     */
    private static RuntimeRunEventDTO attachUsage(RuntimeRunEventDTO event, AgentRunBo request,
                                                  Instant[] startedAt, StringBuilder answer, RunUsage[] usage,
                                                  ReportedUsage reportedUsage) {
        if (event.type() != RuntimeEventType.RUN_COMPLETED) {
            return event;
        }
        var computed = usageOf(request, startedAt[0], answer.toString(), reportedUsage);
        usage[0] = computed;
        return RuntimeRunEventDTO.completed(event.runId(), computed.toJson());
    }

    /**
     * 用量埋点：写 v5ai_model_usage 明细 + 递增应用配额记账。
     */
    private void recordUsage(AgentRunBo request, String runId, Long modelId, String modelName,
                             RunUsage usage, RunStatus status) {
        if (usageRecorder == null && quotaService == null) {
            return;
        }
        try {
            if (usageRecorder != null) {
                usageRecorder.record(new ModelUsageDTO(
                        null, runId, request.agentKey(),
                        modelId,
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

    private static ModelCallInfo parseModelCall(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ModelCallInfo(null, payload);
        }
        try {
            var json = JSONUtil.parseObj(payload);
            if (json.containsKey("modelName")) {
                return new ModelCallInfo(json.getLong("modelId"),
                        json.getStr("modelKey", json.getStr("modelName")));
            }
        } catch (RuntimeException ignored) {
            // Compatibility with pre-change MODEL_CALL events whose payload was plain text.
        }
        return new ModelCallInfo(null, payload);
    }

    private record ModelCallInfo(Long modelId, String modelKey) {
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
