package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentRuntime;
import xin.v5ai.nb.common.agentscope.core.domain.*;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.vo.AgentRunVo;
import xin.v5ai.nb.common.agentscope.core.service.MessageService;
import xin.v5ai.nb.common.agentscope.core.service.RunRecordService;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 思考增量不落库：它是模型的中间产物、不是回答的一部分，落库只会让 v5ai_run_event
 * 随思考长度膨胀，且没有第二个消费方。与「知识库问答整体不持久化」同一口径
 * （见 CONTEXT.md「AgentDTO 调试」）。
 */
class PersistingAgentRuntimeTest {

    /** 委托运行时：按固定顺序回放事件，不触碰任何外部依赖。 */
    private static final class ScriptedRuntime implements AgentRuntime {
        private final List<RuntimeEvent> events;

        ScriptedRuntime(List<RuntimeEvent> events) {
            this.events = events;
        }

        @Override
        public Flux<RuntimeEvent> stream(AgentRunBo request) {
            return Flux.fromIterable(events);
        }

        @Override
        public Mono<AgentRunVo> call(AgentRunBo request) {
            return Mono.empty();
        }
    }

    /** 只收集落库消息与运行记录，不做任何断言之外的事。 */
    private static final class RecordingMessageService implements MessageService {
        private final List<SessionMessage> saved = new ArrayList<>();

        @Override
        public void save(SessionMessage message) {
            saved.add(message);
        }

        @Override
        public List<SessionMessage> findByConversationId(String conversationId) {
            return List.of();
        }
    }

    private static final class RecordingRunRecordService implements RunRecordService {
        private final List<String> started = new ArrayList<>();

        @Override
        public void start(String runId, AgentRunBo request) {
            started.add(runId);
        }

        @Override
        public void complete(String runId) {
        }

        @Override
        public void fail(String runId, String message) {
        }
    }

    @Test
    void reasoningDeltaIsNotPersistedWhileOtherEventsAre() {
        var runId = "run-1";
        var scripted = List.of(
                RuntimeEvent.started(runId),
                RuntimeEvent.reasoningDelta(runId, "先看需求"),
                RuntimeEvent.reasoningDelta(runId, "再给结论"),
                RuntimeEvent.textDelta(runId, "答案是 42"),
                RuntimeEvent.completed(runId));

        var persisted = new ArrayList<RuntimeEvent>();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                new RecordingMessageService(),
                new RecordingRunRecordService(),
                conversation -> { },
                persisted::add);

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        // 思考增量不落库；其余事件照常落库
        assertThat(persisted).noneMatch(event -> event.type() == RuntimeEventType.REASONING_DELTA);
        assertThat(persisted).extracting(RuntimeEvent::type).containsExactly(
                RuntimeEventType.RUN_STARTED,
                RuntimeEventType.TEXT_DELTA,
                RuntimeEventType.RUN_COMPLETED);
    }

    @Test
    void reasoningContentNeverLeaksIntoThePersistedAnswer() {
        var runId = "run-2";
        var scripted = List.of(
                RuntimeEvent.started(runId),
                RuntimeEvent.reasoningDelta(runId, "这段是思考，不该进回答"),
                RuntimeEvent.textDelta(runId, "正文"),
                RuntimeEvent.completed(runId));

        var messages = new RecordingMessageService();
        var runtime = new PersistingAgentRuntime(
                new ScriptedRuntime(scripted),
                messages,
                new RecordingRunRecordService(),
                conversation -> { },
                event -> { });

        runtime.stream(new AgentRunBo("agent-x", "conv-2", "问题")).collectList().block();

        // save 顺序：用户提问 → 助手回答（RUN_COMPLETED 时落一条）
        assertThat(messages.saved).hasSize(2);
        assertThat(messages.saved.get(1).content()).isEqualTo("正文");
    }
}
