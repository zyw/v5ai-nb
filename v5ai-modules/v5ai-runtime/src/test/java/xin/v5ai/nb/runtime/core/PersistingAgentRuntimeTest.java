package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.model.api.domain.ModelUsageDTO;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.core.service.MessageService;
import xin.v5ai.nb.runtime.core.service.RunRecordService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久化装饰器的副作用口径：
 * <ul>
 *   <li>思考增量不落库（它不是回答的一部分，落库只会让 v5ai_run_event 膨胀）；</li>
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

        @Override
        public boolean save(SessionMessage message) {
            return saved.add(message);
        }

        @Override
        public List<SessionMessage> findByConversationId(String conversationId) {
            return List.of();
        }

        @Override
        public Map<String, String> findFirstUserMessageByConversationIds(Collection<String> conversationIds) {
            return Map.of();
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

    /** 不持久化会话的空实现。 */
    private static final class NoopConversations implements ConversationService {
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
                .extracting(SessionMessage::content)
                .isEqualTo("半截回答");
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
}
