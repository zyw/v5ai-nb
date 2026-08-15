package xin.v5ai.nb.runtime;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
public class PersistingAgentRuntime implements AgentRuntime {
    /** 被装饰的真实运行时（实际执行 Agent） */
    private final AgentRuntime delegate;
    /** 消息仓储：保存用户提问与助手回答 */
    private final MessageRepository messageRepository;
    /** 运行记录仓储：记录一次运行的 开始/完成/失败 生命周期 */
    private final RunRecordRepository runRecordRepository;
    /** 会话仓储：确保会话记录存在 */
    private final ConversationRepository conversationRepository;
    /** 运行事件仓储：保存每一条运行事件 */
    private final RunEventRepository runEventRepository;
    /** Agent 状态仓储（可空）：保存会话级 Agent 状态 */
    private final AgentStateRepository agentStateRepository;

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository
    ) {
        // 最简构造：会话/事件仓储用空实现，不持久化这两类数据
        this(delegate, messageRepository, runRecordRepository, conversation -> {}, event -> {}, null);
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository,
            ConversationRepository conversationRepository,
            RunEventRepository runEventRepository
    ) {
        this(delegate, messageRepository, runRecordRepository, conversationRepository, runEventRepository, null);
    }

    public PersistingAgentRuntime(
            AgentRuntime delegate,
            MessageRepository messageRepository,
            RunRecordRepository runRecordRepository,
            ConversationRepository conversationRepository,
            RunEventRepository runEventRepository,
            AgentStateRepository agentStateRepository
    ) {
        this.delegate = delegate;
        this.messageRepository = messageRepository;
        this.runRecordRepository = runRecordRepository;
        this.conversationRepository = conversationRepository;
        this.runEventRepository = runEventRepository;
        this.agentStateRepository = agentStateRepository;
    }

    @Override
    public Flux<RuntimeEvent> stream(AgentRunRequest request) {
        // defer：订阅时才执行副作用（落库），避免每次构建 Flux 就写库
        return Flux.defer(() -> {
            // 若未传 conversationId，则先自动生成一个（normalize 负责）
            var normalizedRequest = normalize(request);
            // 1) 登记会话 + 保存用户提问
            conversationRepository.ensureConversation(new Conversation(normalizedRequest.conversationId(), normalizedRequest.appKey()));
            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.USER, normalizedRequest.query()));
            // 累积最终回答文本（把 TEXT_DELTA 片段拼起来）
            var answer = new StringBuilder();
            // 用单元素数组持有 runId（lambda 里捕获可变值）
            var runId = new String[1];
            return delegate.stream(normalizedRequest)
                    .doOnNext(event -> {
                        // 外键约束：v5ai_run_event.run_id 引用 v5ai_run.id，
                        // 必须先插入 run 记录再保存其事件
                        if (event.type() == RuntimeEventType.RUN_STARTED) {
                            runId[0] = event.runId();
                            runRecordRepository.start(event.runId(), normalizedRequest);
                        }
                        // 每一条事件都落库
                        runEventRepository.save(event);
                        if (event.type() == RuntimeEventType.TEXT_DELTA) {
                            answer.append(event.payload());
                        }
                        if (event.type() == RuntimeEventType.MESSAGE_COMPLETED) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                        }
                        if (event.type() == RuntimeEventType.RUN_COMPLETED) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                            runRecordRepository.complete(event.runId());
                        }
                        if (event.type() == RuntimeEventType.RUN_FAILED) {
                            runRecordRepository.fail(event.runId(), event.payload());
                        }
                    })
                    // 异常兜底：把异常转成 RUN_FAILED 事件，同样落库并标记 run 失败
                    .onErrorResume(exception -> {
                        var failedRunId = runId[0] == null ? UUID.randomUUID().toString() : runId[0];
                        if (runId[0] == null) {
                            // 错误发生在 RUN_STARTED 之前：先建 run 记录，
                            // 才能让随后的失败事件满足外键约束
                            runRecordRepository.start(failedRunId, normalizedRequest);
                        }
                        runEventRepository.save(RuntimeEvent.failed(failedRunId, exception.getMessage()));
                        runRecordRepository.fail(failedRunId, exception.getMessage());
                        return Flux.just(RuntimeEvent.failed(failedRunId, exception.getMessage()));
                    })
                    // 正常结束但从未收到 RUN_STARTED（delegate 没发启动事件）时，
                    // 也要把回答落库，保证消息完整
                    .doOnComplete(() -> {
                        if (runId[0] == null) {
                            messageRepository.save(new Message(normalizedRequest.conversationId(), normalizedRequest.appKey(), MessageRole.ASSISTANT, answer.toString()));
                            saveAgentState(normalizedRequest, answer.toString());
                        }
                    });
        });
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
     * 保存会话级 Agent 状态（可选能力）：把 appKey 与最后一次回答写入状态仓储。
     * 未配置 agentStateRepository 时直接跳过。
     */
    private void saveAgentState(AgentRunRequest request, String answer) {
        if (agentStateRepository == null) {
            return;
        }
        // 序列化为简单的 JSON 字符串，并对回答中的引号做转义
        agentStateRepository.save(request.conversationId(),
                """
                {"appKey":"%s","lastAnswer":"%s"}
                """.formatted(request.appKey(), answer == null ? "" : answer.replace("\"", "\\\"")).trim());
    }

    /**
     * 规范化请求：conversationId 缺失或为空时自动生成一个 UUID，
     * 保证每次运行都能关联到唯一会话。
     */
    private AgentRunRequest normalize(AgentRunRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request;
        }
        return new AgentRunRequest(request.appKey(), UUID.randomUUID().toString(), request.query(),
                request.ragContext(), request.history(), request.runId());
    }
}
