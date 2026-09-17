package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.domain.*;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.runtime.core.service.MessageService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力开关生效验证：ragEnabled 门控检索上下文构建；memoryEnabled 门控会话历史注入，
 * 且显式携带历史（resume）时不覆盖。
 */
class AgentScopeRuntimeSwitchesTest {

    /** 捕获传给执行器的请求，便于断言门控后的历史/上下文。 */
    private static final class CapturingExecutor implements AgentTextExecutor {
        AgentRunBo lastRequest;

        @Override
        public Flux<AgentTextEvent> streamText(AgentDTO agent, AgentRunBo request) {
            this.lastRequest = request;
            return Flux.just(new AgentTextEvent.Text("ok"));
        }
    }

    /** 内存 MessageService，按 conversationId 存/取。 */
    private static final class InMemoryMessageService implements MessageService {
        private final List<SessionMessage> messages = new ArrayList<>();

        InMemoryMessageService(List<SessionMessage> seed) {
            messages.addAll(seed);
        }

        @Override
        public boolean save(SessionMessage message) {
            return messages.add(message);
        }

        @Override
        public List<SessionMessage> findByConversationId(String conversationId) {
            List<SessionMessage> result = new ArrayList<>();
            for (SessionMessage m : messages) {
                if (m.conversationId().equals(conversationId)) {
                    result.add(m);
                }
            }
            return result;
        }
    }

    private static AgentDTO agent(boolean ragEnabled, boolean memoryEnabled) {
        return agent(ragEnabled, memoryEnabled, RagCallMode.FORCED.value());
    }

    private static AgentDTO agent(boolean ragEnabled, boolean memoryEnabled, int ragCallMode) {
        return new AgentDTO("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null,
                memoryEnabled, false, false, false, ragEnabled, ragCallMode);
    }

    @Test
    void ragDisabledSkipsContextBuilding() {
        var counter = new AtomicInteger();
        var executor = new CapturingExecutor();
        var runtime = new AgentScopeRuntime(
                key -> agent(false, false),
                executor,
                (agent, request) -> {
                    counter.incrementAndGet();
                    return Mono.just(new RagContext("上下文", List.of(new RagHit(1L, 2L, "doc", 0, "内容", 0.9))));
                },
                new InMemoryMessageService(List.of()));

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        assertThat(counter).hasValue(0);
        assertThat(events).noneMatch(event -> event.type() == RuntimeEventType.RETRIEVAL);
        assertThat(executor.lastRequest.ragContext()).isNull();
    }

    @Test
    void memoryEnabledInjectsPriorHistoryWithoutDuplicatingCurrentTurn() {
        var executor = new CapturingExecutor();
        // PersistingAgentRuntime 已先落库当前用户消息：尾部 USER=当前 query，应被剔除
        var seed = List.of(
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "你好"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "你好！"),
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "问题"));
        var runtime = new AgentScopeRuntime(
                key -> agent(false, true),
                executor,
                null,
                new InMemoryMessageService(seed));

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        assertThat(executor.lastRequest.history()).extracting(SessionMessage::role, SessionMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MessageRole.USER, "你好"),
                        org.assertj.core.groups.Tuple.tuple(MessageRole.ASSISTANT, "你好！"));
    }

    @Test
    void memoryDisabledLeavesHistoryEmpty() {
        var executor = new CapturingExecutor();
        var seed = List.of(
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "你好"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "你好！"));
        var runtime = new AgentScopeRuntime(
                key -> agent(false, false),
                executor,
                null,
                new InMemoryMessageService(List.copyOf(seed)));

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        assertThat(executor.lastRequest.history()).isEmpty();
    }

    @Test
    void explicitHistoryIsNeverOverwrittenByMemoryLoader() {
        var executor = new CapturingExecutor();
        var seed = List.of(new SessionMessage("conv-1", "agent-x", MessageRole.USER, "已被显式覆盖的历史"));
        var runtime = new AgentScopeRuntime(
                key -> agent(false, true),
                executor,
                null,
                new InMemoryMessageService(seed));

        var explicit = List.of(new SessionMessage("conv-1", "agent-x", MessageRole.USER, "resume 显式历史"));
        runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题").withHistory(explicit)).collectList().block();

        assertThat(executor.lastRequest.history()).extracting(SessionMessage::content).containsExactly("resume 显式历史");
    }

    @Test
    void smartRagSkipsPreRetrieval() {
        var counter = new AtomicInteger();
        var executor = new CapturingExecutor();
        var runtime = new AgentScopeRuntime(
                key -> agent(true, false, RagCallMode.SMART.value()),
                executor,
                (agent, request) -> {
                    counter.incrementAndGet();
                    return Mono.just(new RagContext("上下文", List.of(new RagHit(1L, 2L, "doc", 0, "内容", 0.9))));
                },
                new InMemoryMessageService(List.of()),
                modelId -> true);

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        assertThat(counter).hasValue(0);
        assertThat(events).noneMatch(event -> event.type() == RuntimeEventType.RETRIEVAL);
        assertThat(executor.lastRequest.ragContext()).isNull();
    }

    @Test
    void smartRagFallsBackToForcedWhenModelLacksTools() {
        var counter = new AtomicInteger();
        var executor = new CapturingExecutor();
        var runtime = new AgentScopeRuntime(
                key -> agent(true, false, RagCallMode.SMART.value()),
                executor,
                (agent, request) -> {
                    counter.incrementAndGet();
                    return Mono.just(new RagContext("上下文", List.of(new RagHit(1L, 2L, "doc", 0, "内容", 0.9))));
                },
                new InMemoryMessageService(List.of()),
                modelId -> false);

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        assertThat(counter).hasValue(1);
        assertThat(events).anyMatch(event -> event.type() == RuntimeEventType.RETRIEVAL);
        assertThat(executor.lastRequest.ragContext()).isEqualTo("上下文");
    }
}