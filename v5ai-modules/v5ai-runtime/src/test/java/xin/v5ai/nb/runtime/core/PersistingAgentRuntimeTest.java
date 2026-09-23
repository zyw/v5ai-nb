package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.rag.CitationPayload;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.model.api.domain.ModelUsageDTO;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.core.service.RunRecordService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 持久化装饰器的副作用口径：
 * <ul>
 *   <li>思考增量不写 v5ai_run_event（它会让事件表膨胀），但**随所属助手消息落库**供回看；</li>
 *   <li>助手回答只落库一次（取消与正常完成可能先后到达）；</li>
 *   <li>取消是正常收尾：标记 CANCELED、保留半截回答、照样记用量。</li>
 * </ul>
 */
class PersistingAgentRuntimeTest {

    /** 委托运行时：按固定顺序回放事件，不触碰任何外部依赖。 */
    private static final class ScriptedRuntime implements AgentRuntime {
        private final List<RuntimeRunEventDTO> events;

        ScriptedRuntime(List<RuntimeRunEventDTO> events) {
            this.events = events;
        }

        @Override
        public Flux<RuntimeRunEventDTO> stream(AgentRunBo request) {
            return Flux.fromIterable(events);
        }

        @Override
        public Mono<AgentRunVo> call(AgentRunBo request) {
            return Mono.empty();
        }
    }

    /** 委托运行时：把外部可写的数据流交给装饰器，便于在中途触发取消。 */
    private static final class PipedRuntime implements AgentRuntime {
        private final Flux<RuntimeRunEventDTO> events;

        PipedRuntime(Flux<RuntimeRunEventDTO> events) {
            this.events = events;
        }

        @Override
        public Flux<RuntimeRunEventDTO> stream(AgentRunBo request) {
            return events;
        }

        @Override
        public Mono<AgentRunVo> call(AgentRunBo request) {
            return Mono.empty();
        }
    }

    /** 只收集落库消息，不做任何断言之外的事。 */
    private static final class RecordingMessageService implements MessageService {
        private final List<SessionMessage> saved = new ArrayList<>();
        /** 收到的「作废锚点之后的消息」调用参数（会话#锚点） */
        final List<String> supersededAfter = new ArrayList<>();

        @Override
        public Long save(SessionMessage message) {
            saved.add(message);
            // 递增的确定性 id：断言 RUN_STARTED 载荷时要用
            return (long) saved.size();
        }

        @Override
        public List<SessionMessage> findByConversationId(String conversationId) {
            return List.of();
        }

        @Override
        public List<SessionMessage> findRecentByConversationId(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<SessionMessage> findActiveBetween(String conversationId, Long afterMessageId,
                                                      Long beforeMessageId, int limit) {
            return List.of();
        }

        @Override
        public Optional<SessionMessage> findActiveMessage(Long messageId) {
            return Optional.empty();
        }

        @Override
        public int supersedeAfter(String conversationId, Long messageId) {
            supersededAfter.add(conversationId + "#" + messageId);
            return 1;
        }
    }

    private static final class RecordingRunRecordService implements RunRecordService {
        private final List<String> started = new ArrayList<>();
        private final List<String> canceled = new ArrayList<>();

        @Override
        public boolean start(String runId, AgentRunBo request) {
            return started.add(runId);
        }

        @Override
        public boolean complete(String runId) {
            return true;
        }

        @Override
        public boolean fail(String runId, String message) {
            return true;
        }

        @Override
        public boolean cancel(String runId) {
            return canceled.add(runId);
        }

        @Override
        public Optional<String> findConversationId(String runId) {
            return Optional.of("conv-1");
        }
    }

    /** 不持久化会话的空实现：既不建会话，也就不会触发标题改写。 */
    private static final class NoopConversations implements ConversationService {
        @Override
        public boolean ensureConversation(ConversationDTO conversation) {
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
    }

    /** 记录被要求「保证存在」的会话，并按需回答「这次是不是新建」。 */
    private static final class RecordingConversations implements ConversationService {
        private final boolean createsOnEnsure;
        final List<ConversationDTO> ensured = new ArrayList<>();

        RecordingConversations(boolean createsOnEnsure) {
            this.createsOnEnsure = createsOnEnsure;
        }

        @Override
        public boolean ensureConversation(ConversationDTO conversation) {
            ensured.add(conversation);
            return createsOnEnsure;
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
    }

    @Test
    void reasoningDeltaIsNotPersistedWhileOtherEventsAre() {
        var runId = "run-1";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.reasoningDelta(runId, "先看需求"),
                RuntimeRunEventDTO.reasoningDelta(runId, "再给结论"),
                RuntimeRunEventDTO.textDelta(runId, "答案是 42"),
                RuntimeRunEventDTO.completed(runId));

        var persisted = new ArrayList<RuntimeRunEventDTO>();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                new RecordingMessageService(),
                new RecordingRunRecordService(),
                new NoopConversations(),
                persisted::add);

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        // 思考增量不落库；其余事件照常落库
        assertThat(persisted).noneMatch(event -> event.type() == RuntimeEventType.REASONING_DELTA);
        assertThat(persisted).extracting(RuntimeRunEventDTO::type).containsExactly(
                RuntimeEventType.RUN_STARTED,
                RuntimeEventType.TEXT_DELTA,
                RuntimeEventType.RUN_COMPLETED);
    }

    @Test
    void reasoningContentNeverLeaksIntoThePersistedAnswer() {
        var runId = "run-2";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.reasoningDelta(runId, "这段是思考，不该进回答"),
                RuntimeRunEventDTO.textDelta(runId, "正文"),
                RuntimeRunEventDTO.completed(runId));

        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                messages,
                new RecordingRunRecordService(),
                new NoopConversations(),
                event -> {
                    return false;
                });

        runtime.stream(new AgentRunBo("agent-x", "conv-2", "问题")).collectList().block();

        // save 顺序：用户提问 → 助手回答（且只落一条，不因 MESSAGE_COMPLETED 与 RUN_COMPLETED 重复）
        assertThat(messages.saved).hasSize(2);
        assertThat(messages.saved.get(1).content()).isEqualTo("正文");
    }

    /**
     * 引用随回答落库：一次运行可能发多条 RETRIEVAL（智能调用下每次 rag_search 之后一条），
     * 按 (knowledgeBaseId, documentId, chunkIndex) 保序去重、保留首次命中——历史回看要
     * 与当时实时所见一致（见 docs/adr/0009）。
     */
    @Test
    void citationsAreAccumulatedDeduplicatedAndPersistedWithTheAnswer() {
        var runId = "run-citations";
        var first = new RagHit(5L, 13L, "文档A.pdf", 0, "切片一", 0.21);
        var second = new RagHit(5L, 13L, "文档A.pdf", 20, "切片二", 0.30);
        // 第三次检索又命中「切片一」（分数不同）：应当只保留首次那条及其分数
        var repeated = new RagHit(5L, 13L, "文档A.pdf", 0, "切片一", 0.99);
        var third = new RagHit(4L, 12L, "文档B.pdf", 3, "切片三", 0.44);
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.retrieval(runId, CitationPayload.toJson(List.of(first, second))),
                RuntimeRunEventDTO.retrieval(runId, CitationPayload.toJson(List.of(repeated, third))),
                RuntimeRunEventDTO.textDelta(runId, "正文"),
                RuntimeRunEventDTO.completed(runId));

        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                messages,
                new RecordingRunRecordService(),
                new NoopConversations(),
                event -> false);

        runtime.stream(new AgentRunBo("agent-x", "conv-citations", "问题")).collectList().block();

        assertThat(messages.saved).hasSize(2);
        assertThat(messages.saved.get(1).citations())
                .extracting(RagHit::chunkIndex, RagHit::score)
                .containsExactly(tuple(0, 0.21), tuple(20, 0.30), tuple(3, 0.44));
    }

    /** 没有检索的一轮：引用为空列表，落库侧写 null（= 这一轮没有引用，不是「空引用」）。 */
    @Test
    void turnsWithoutRetrievalCarryNoCitations() {
        var runId = "run-no-citations";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "正文"),
                RuntimeRunEventDTO.completed(runId));

        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                messages,
                new RecordingRunRecordService(),
                new NoopConversations(),
                event -> false);

        runtime.stream(new AgentRunBo("agent-x", "conv-plain", "问题")).collectList().block();

        assertThat(messages.saved.get(1).citations()).isEmpty();
    }

    /** 载荷损坏（不是 JSON 数组）时引用为空：引用解析失败绝不能把对话主链路带崩。 */
    @Test
    void malformedRetrievalPayloadDegradesToNoCitations() {
        var runId = "run-broken-citations";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.retrieval(runId, "not-a-json-array"),
                RuntimeRunEventDTO.textDelta(runId, "正文"),
                RuntimeRunEventDTO.completed(runId));

        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                messages,
                new RecordingRunRecordService(),
                new NoopConversations(),
                event -> false);

        runtime.stream(new AgentRunBo("agent-x", "conv-broken", "问题")).collectList().block();

        assertThat(messages.saved.get(1).citations()).isEmpty();
    }

    @Test
    void assistantAnswerIsPersistedOnlyOnceEvenWhenBothTerminalEventsArrive() {
        var runId = "run-3";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "正文"),
                RuntimeRunEventDTO.completed(runId));

        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false);

        runtime.stream(new AgentRunBo("agent-x", "conv-3", "问题")).collectList().block();

        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT).hasSize(1);
    }

    /**
     * 停止对话：取消后运行标记 CANCELED、已生成的部分回答照样落库、用量按已生成内容记账
     * （否则「点停止」就成了免费额度漏洞），且不会补发 RUN_COMPLETED。
     */
    @Test
    void canceledRunIsMarkedCanceledKeepsPartialAnswerAndStillRecordsUsage() {
        var runId = "run-cancel";
        Sinks.Many<RuntimeRunEventDTO> sink = Sinks.many().multicast().onBackpressureBuffer();
        var messages = new RecordingMessageService();
        var runs = new RecordingRunRecordService();
        var usages = new ArrayList<ModelUsageDTO>();
        var registry = new RunCancellationRegistry();
        var runtime = new PersistingAgentRuntime(new PipedRuntime(sink.asFlux()), messages, runs,
                new NoopConversations(), event -> false, null, usage -> {
            usages.add(usage);
            return true;
        }, null, registry);

        var received = new ArrayList<RuntimeRunEventDTO>();
        var subscription = runtime.stream(new AgentRunBo("agent-x", "conv-4", "问题"))
                .subscribe(received::add);
        sink.tryEmitNext(RuntimeRunEventDTO.started(runId));
        sink.tryEmitNext(RuntimeRunEventDTO.modelCall(runId, "gpt-4o"));
        sink.tryEmitNext(RuntimeRunEventDTO.textDelta(runId, "半截回答"));

        assertThat(registry.cancel(runId)).isTrue();

        assertThat(subscription.isDisposed()).isTrue();
        assertThat(received).extracting(RuntimeRunEventDTO::type).doesNotContain(RuntimeEventType.RUN_COMPLETED);
        assertThat(runs.canceled).containsExactly(runId);
        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(answer -> {
                    assertThat(answer.content()).isEqualTo("半截回答");
                    // 被停止的回答同样带着用量落库：停止不是"这一次没消耗"
                    assertThat(answer.usage()).isNotNull();
                    assertThat(answer.usage().durationMs()).isGreaterThanOrEqualTo(0L);
                });
        assertThat(usages).singleElement().extracting(ModelUsageDTO::status).isEqualTo("CANCELED");
        assertThat(usages.get(0).completionTokens()).isPositive();
    }

    /** 取消之后再点一次停止：运行已经不在登记处，既不会报错也不会重复落库。 */
    @Test
    void stoppingAnAlreadyFinishedRunDoesNotTouchThePersistedAnswerAgain() {
        var runId = "run-cancel-twice";
        Sinks.Many<RuntimeRunEventDTO> sink = Sinks.many().multicast().onBackpressureBuffer();
        var messages = new RecordingMessageService();
        var runs = new RecordingRunRecordService();
        var registry = new RunCancellationRegistry();
        var runtime = new PersistingAgentRuntime(new PipedRuntime(sink.asFlux()), messages, runs,
                new NoopConversations(), event -> false, null, null, null, registry);

        runtime.stream(new AgentRunBo("agent-x", "conv-5", "问题")).subscribe();
        sink.tryEmitNext(RuntimeRunEventDTO.started(runId));
        sink.tryEmitNext(RuntimeRunEventDTO.textDelta(runId, "半截"));
        registry.cancel(runId);

        assertThat(registry.cancel(runId)).isFalse();
        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT).hasSize(1);
        assertThat(runs.canceled).containsExactly(runId);
    }

    /**
     * RUN_COMPLETED 的载荷带用量汇总：前端「用量 / 用时」直接读它，
     * 且必须与写进 v5ai_model_usage 的记账是**同一份**数字（否则界面和账目会各说一套）。
     */
    @Test
    void completedRunCarriesUsageInTheCompletionPayload() {
        var runId = "run-usage";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.modelCall(runId, "gpt-4o"),
                RuntimeRunEventDTO.textDelta(runId, "12345678"),
                RuntimeRunEventDTO.completed(runId));
        var usages = new ArrayList<ModelUsageDTO>();
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false, null, usage -> {
            usages.add(usage);
            return true;
        }, null, new RunCancellationRegistry());

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-7", "问题")).collectList().block();

        var completed = events.stream()
                .filter(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .findFirst()
                .orElseThrow();
        var payload = cn.hutool.json.JSONUtil.parseObj(completed.payload());
        // 8 个 ASCII 字符 → 估算 2 个完成 token；提问「问题」是 2 个汉字 → 2 个 prompt token
        // （估算口径：CJK 按 1 字 1 token，其余 4 字符 1 token）
        assertThat(payload.getLong("completionTokens")).isEqualTo(2L);
        assertThat(payload.getLong("promptTokens")).isEqualTo(2L);
        assertThat(payload.getLong("totalTokens")).isEqualTo(4L);
        assertThat(payload.getLong("durationMs")).isGreaterThanOrEqualTo(0L);
        // 载荷与记账同源：同一份数字，不各估一套
        assertThat(usages).singleElement().satisfies(usage -> {
            assertThat(usage.completionTokens()).isEqualTo(2L);
            assertThat(usage.durationMs()).isEqualTo(payload.getLong("durationMs"));
        });
        // 用量也要跟着回答落库，否则刷新页面后历史回答就没了这两个数字
        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(answer -> {
                    assertThat(answer.usage()).isNotNull();
                    assertThat(answer.usage().completionTokens()).isEqualTo(2L);
                    assertThat(answer.usage().totalTokens()).isEqualTo(4L);
                    assertThat(answer.usage().durationMs()).isEqualTo(payload.getLong("durationMs"));
                });
    }

    /**
     * 执行器回报的真实用量优先于估算：工具循环里的多轮调用**累加**，估算值只作为没有真实值时的兜底；
     * 用量事件本身是平台内部事件——既不落库，也不出现在对外的流里。
     */
    @Test
    void reportedModelUsageWinsOverTheEstimateAndStaysInternal() {
        var runId = "run-real-usage";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.modelCall(runId, "gpt-4o"),
                // 执行器在流开头发出的估算（先到）
                RuntimeRunEventDTO.modelUsage(runId, 50, 0, true),
                // 两次模型调用（工具循环）的真实值
                RuntimeRunEventDTO.modelUsage(runId, 120, 30, false),
                RuntimeRunEventDTO.modelUsage(runId, 300, 40, false),
                RuntimeRunEventDTO.textDelta(runId, "答"),
                RuntimeRunEventDTO.completed(runId));
        var persistedEvents = new ArrayList<RuntimeRunEventDTO>();
        var usages = new ArrayList<ModelUsageDTO>();
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), persistedEvents::add, null,
                usage -> {
                    usages.add(usage);
                    return true;
                }, null, new RunCancellationRegistry());

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-usage", "问题")).collectList().block();

        assertThat(events).isNotNull();
        // 不外发：对外的流里一条用量事件都没有
        assertThat(events).noneMatch(event -> event.type() == RuntimeEventType.MODEL_USAGE);
        // 不落库：v5ai_run_event 同样没有它
        assertThat(persistedEvents).noneMatch(event -> event.type() == RuntimeEventType.MODEL_USAGE);
        var completed = events.stream()
                .filter(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .findFirst()
                .orElseThrow();
        var payload = cn.hutool.json.JSONUtil.parseObj(completed.payload());
        assertThat(payload.getLong("promptTokens")).isEqualTo(420L);
        assertThat(payload.getLong("completionTokens")).isEqualTo(70L);
        // 载荷、助手消息、用量明细三处同源
        assertThat(usages).singleElement().satisfies(usage -> {
            assertThat(usage.promptTokens()).isEqualTo(420L);
            assertThat(usage.completionTokens()).isEqualTo(70L);
        });
        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(answer -> assertThat(answer.usage().promptTokens()).isEqualTo(420L));
    }

    /** 思考随回答一起落库：刷新页面后门户仍能折叠回看。 */
    @Test
    void reasoningIsPersistedWithTheAnswer() {
        var runId = "run-reasoning";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.reasoningDelta(runId, "先看 A，"),
                RuntimeRunEventDTO.reasoningDelta(runId, "再看 B"),
                RuntimeRunEventDTO.textDelta(runId, "结论"),
                RuntimeRunEventDTO.completed(runId));
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false);

        runtime.stream(new AgentRunBo("agent-x", "conv-12", "问题")).collectList().block();

        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(answer -> {
                    assertThat(answer.content()).isEqualTo("结论");
                    assertThat(answer.reasoning()).isEqualTo("先看 A，再看 B");
                });
    }

    /** 被停止时连思考也保留半截（与「半截回答照常落库」同一口径）。 */
    @Test
    void canceledRunKeepsThePartialReasoning() {
        var runId = "run-reasoning-cancel";
        Sinks.Many<RuntimeRunEventDTO> sink = Sinks.many().multicast().onBackpressureBuffer();
        var messages = new RecordingMessageService();
        var registry = new RunCancellationRegistry();
        var runtime = new PersistingAgentRuntime(new PipedRuntime(sink.asFlux()), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false, null, null, null,
                registry);

        runtime.stream(new AgentRunBo("agent-x", "conv-13", "问题")).subscribe();
        sink.tryEmitNext(RuntimeRunEventDTO.started(runId));
        sink.tryEmitNext(RuntimeRunEventDTO.reasoningDelta(runId, "想到一半"));
        sink.tryEmitNext(RuntimeRunEventDTO.textDelta(runId, "半截"));
        registry.cancel(runId);

        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(answer -> assertThat(answer.reasoning()).isEqualTo("想到一半"));
    }

    /** 开关关闭：思考照常走事件流，但不落库（给敏感内容/存储增长的部署留的口子）。 */
    @Test
    void reasoningPersistenceCanBeSwitchedOff() {
        var runId = "run-reasoning-off";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.reasoningDelta(runId, "不该落库的思考"),
                RuntimeRunEventDTO.textDelta(runId, "结论"),
                RuntimeRunEventDTO.completed(runId));
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false, null, null, null,
                new RunCancellationRegistry(), null, null, false);

        runtime.stream(new AgentRunBo("agent-x", "conv-14", "问题")).collectList().block();

        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .satisfies(answer -> assertThat(answer.reasoning()).isNull());
    }

    /** 回答落库后请求一次会话摘要（异步端口，这里只验证「按什么参数、只调一次」）。 */
    @Test
    void aCompletedRunSchedulesTheConversationSummaryOnce() {
        var runId = "run-summary";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "答"),
                RuntimeRunEventDTO.completed(runId));
        var summarized = new ArrayList<String>();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), new RecordingMessageService(),
                new RecordingRunRecordService(), new NoopConversations(), event -> false, null, null, null,
                new RunCancellationRegistry(), null,
                (conversationId, agentKey) -> summarized.add(conversationId + "#" + agentKey));

        runtime.stream(new AgentRunBo("agent-x", "conv-11", "问题")).collectList().block();

        assertThat(summarized).containsExactly("conv-11#agent-x");
    }

    /**
     * 「重新生成」复用既有提问：不再新插用户消息（否则历史里会出现两条相同提问），
     * 回答照常落库，且 RUN_STARTED 不带 userMessageId（没有新消息，前端本来就持有那个 id）。
     */
    @Test
    void regenerationReusesTheExistingQuestionInsteadOfInsertingANewOne() {
        var runId = "run-regen";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "新回答"),
                RuntimeRunEventDTO.completed(runId));
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false);

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-8", "原来的提问")
                .withReuseUserMessage(true)).collectList().block();

        assertThat(messages.saved).noneMatch(message -> message.role() == MessageRole.USER);
        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT)
                .singleElement()
                .extracting(SessionMessage::content)
                .isEqualTo("新回答");
        assertThat(events).filteredOn(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .singleElement()
                .extracting(RuntimeRunEventDTO::payload)
                .isEqualTo("");
    }

    /**
     * 普通运行：RUN_STARTED 携带本轮提问的消息 id，前端不用刷新历史就能重新生成这一轮。
     *
     * <p>形态必须是**字符串**：主键是 19 位雪花号，数字形态过一趟浏览器就被四舍五入，
     * 前端照着回传时服务端查无此消息（404「提问不存在」）。</p>
     */
    @Test
    void runStartedCarriesTheUserMessageId() {
        var runId = "run-anchor";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "回答"),
                RuntimeRunEventDTO.completed(runId));
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new NoopConversations(), event -> false);

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-9", "提问")).collectList().block();

        // RecordingMessageService 的 save 返回递增 id：用户提问是第 1 条
        var started = events.stream().filter(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .findFirst().orElseThrow();
        assertThat(cn.hutool.json.JSONUtil.parseObj(started.payload()).getStr("userMessageId")).isEqualTo("1");
    }

    /** 正常完成时也记用量（回归：改造后在完成路径上仍要记账）。 */
    @Test
    void completedRunRecordsUsageOnce() {
        var runId = "run-ok";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.modelCall(runId, "gpt-4o"),
                RuntimeRunEventDTO.textDelta(runId, "正文"),
                RuntimeRunEventDTO.completed(runId));
        var usages = new ArrayList<ModelUsageDTO>();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), new RecordingMessageService(),
                new RecordingRunRecordService(), new NoopConversations(), event -> false, null, usage -> {
            usages.add(usage);
            return true;
        }, null, new RunCancellationRegistry());

        runtime.stream(new AgentRunBo("agent-x", "conv-6", "问题")).collectList().block();

        assertThat(usages).singleElement().extracting(ModelUsageDTO::status).isEqualTo("COMPLETED");
    }

    // ---------- 会话命名（兜底名 + 模型标题） ----------

    /**
     * 首轮：会话创建时就把「首条提问的兜底名」写进会话（门户列表因此不必再查消息表），
     * 回答落库后**只排一次**模型标题改写——MESSAGE_COMPLETED 与 RUN_COMPLETED 都走到落库，
     * 与「回答只落一次」共用同一道 CAS。
     */
    @Test
    void firstTurnWritesTheFallbackNameAndSchedulesTheModelTitleOnce() {
        var runId = "run-first-turn";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "回答"),
                RuntimeRunEventDTO.messageCompleted(runId, ""),
                RuntimeRunEventDTO.completed(runId));
        var conversations = new RecordingConversations(true);
        var titles = new ArrayList<String>();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), new RecordingMessageService(),
                new RecordingRunRecordService(), conversations, event -> false, null, null, null,
                new RunCancellationRegistry(),
                (conversationId, agentKey, question) ->
                        titles.add(conversationId + "|" + agentKey + "|" + question));

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "帮我看看这张图\n第二行不看")).collectList().block();

        assertThat(conversations.ensured).singleElement()
                .satisfies(conversation -> assertThat(conversation.name()).isEqualTo("帮我看看这张图"));
        assertThat(titles).containsExactly("conv-1|agent-x|帮我看看这张图\n第二行不看");
    }

    /** 追问（会话已存在）：不改会话名，也不值得再花一次模型调用。 */
    @Test
    void laterTurnsDoNotRewriteTheConversationTitle() {
        var runId = "run-second-turn";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "回答"),
                RuntimeRunEventDTO.completed(runId));
        var conversations = new RecordingConversations(false);
        var titles = new ArrayList<String>();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), new RecordingMessageService(),
                new RecordingRunRecordService(), conversations, event -> false, null, null, null,
                new RunCancellationRegistry(),
                (conversationId, agentKey, question) -> titles.add(question));

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "追问")).collectList().block();

        // 名称照样算出来交给仓储（写入侧只在「新建」时采用它，已存在的会话不会被改写）
        assertThat(conversations.ensured).singleElement()
                .satisfies(conversation -> assertThat(conversation.name()).isEqualTo("追问"));
        assertThat(titles).isEmpty();
    }

    /** 标题改写炸了也不能影响对话主链路：它只是锦上添花，兜底名已经在库里。 */
    @Test
    void aFailingTitleGeneratorNeverBreaksTheRun() {
        var runId = "run-title-failure";
        var scripted = List.of(
                RuntimeRunEventDTO.started(runId),
                RuntimeRunEventDTO.textDelta(runId, "回答"),
                RuntimeRunEventDTO.completed(runId));
        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(new ScriptedRuntime(scripted), messages,
                new RecordingRunRecordService(), new RecordingConversations(true), event -> false,
                null, null, null, new RunCancellationRegistry(),
                (conversationId, agentKey, question) -> {
                    throw new IllegalStateException("标题服务不可用");
                });

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        assertThat(events).isNotEmpty();
        assertThat(messages.saved).filteredOn(message -> message.role() == MessageRole.ASSISTANT).hasSize(1);
    }
}
