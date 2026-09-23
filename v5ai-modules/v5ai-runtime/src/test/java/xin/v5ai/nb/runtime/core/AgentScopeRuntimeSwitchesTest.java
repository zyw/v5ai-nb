package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.domain.*;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.history.HistoryWindow;
import xin.v5ai.nb.runtime.core.domain.ConversationSummaryDTO;
import xin.v5ai.nb.runtime.core.service.ConversationSummaryService;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;
import xin.v5ai.nb.runtime.core.service.MessageService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        public Long save(SessionMessage message) {
            messages.add(message);
            return (long) messages.size();
        }

        @Override
        public Optional<SessionMessage> findActiveMessage(Long messageId) {
            return Optional.empty();
        }

        @Override
        public int supersedeAfter(String conversationId, Long messageId) {
            return 0;
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

        /** 与 SQL 侧同一口径：limit <= 0 不限，否则只取最近的 limit 条（保持时间正序）。 */
        @Override
        public List<SessionMessage> findRecentByConversationId(String conversationId, int limit) {
            var result = findByConversationId(conversationId);
            if (limit <= 0 || result.size() <= limit) {
                return result;
            }
            return new ArrayList<>(result.subList(result.size() - limit, result.size()));
        }

        /** 会话摘要用：in-memory 实现没有消息 id，按序号近似即可（测试只关心边界与条数）。 */
        @Override
        public List<SessionMessage> findActiveBetween(String conversationId, Long afterMessageId,
                                                     Long beforeMessageId, int limit) {
            var result = findByConversationId(conversationId);
            return limit > 0 && result.size() > limit
                    ? new ArrayList<>(result.subList(0, limit)) : result;
        }
    }

    private static AgentDTO agent(boolean ragEnabled, boolean memoryEnabled) {
        return agent(ragEnabled, memoryEnabled, RagCallMode.FORCED.value());
    }

    private static AgentDTO agent(boolean ragEnabled, boolean memoryEnabled, int ragCallMode) {
        return new AgentDTO("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null,
                memoryEnabled, false, false, false, ragEnabled, ragCallMode, null, true);
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

    /** 窗口裁剪对「从库里加载」与「调用方显式传入（resume）」一视同仁，统计随 RUN_STARTED 发出。 */
    @Test
    void historyWindowTrimsLoadedHistoryAndReportsItOnRunStarted() {
        var executor = new CapturingExecutor();
        var seed = List.of(
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "问1"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "答1"),
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "问2"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "答2"),
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "问题"));
        var runtime = new AgentScopeRuntime(
                key -> agent(false, true),
                executor,
                null,
                new InMemoryMessageService(seed),
                null,
                new HistoryWindow(2, 10_000, 1, true));

        var events = runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();

        // 尾部 USER 是本轮提问，先剔除；再按窗口只留最近 2 条
        assertThat(executor.lastRequest.history()).extracting(SessionMessage::content)
                .containsExactly("问2", "答2");
        var started = events.stream()
                .filter(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .findFirst()
                .orElseThrow();
        var context = cn.hutool.json.JSONUtil.parseObj(started.payload()).getJSONObject("context");
        assertThat(context.getInt("keptMessages")).isEqualTo(2);
        assertThat(context.getInt("droppedMessages")).isEqualTo(2);
        assertThat(context.getBool("trimmed")).isTrue();
    }

    @Test
    void explicitResumeHistoryIsAlsoTrimmed() {
        var executor = new CapturingExecutor();
        var runtime = new AgentScopeRuntime(
                key -> agent(false, true),
                executor,
                null,
                new InMemoryMessageService(List.of()),
                null,
                new HistoryWindow(2, 10_000, 1, true));
        var explicit = List.of(
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "问1"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "答1"),
                new SessionMessage("conv-1", "agent-x", MessageRole.USER, "问2"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "答2"));

        runtime.stream(new AgentRunBo("agent-x", "conv-1", "问题").withHistory(explicit)).collectList().block();

        assertThat(executor.lastRequest.history()).extracting(SessionMessage::content)
                .containsExactly("问2", "答2");
    }

    /** 会话摘要只在开启记忆时注入：它就是「本会话更早的历史」，关掉记忆就不该有它的影子。 */
    @Test
    void conversationSummaryIsInjectedOnlyWhenMemoryIsEnabled() {
        var summary = new ConversationSummaryDTO("conv-1", "更早的内容摘要", 5L, 3, 7L, OffsetDateTime.now());

        var enabledExecutor = new CapturingExecutor();
        newRuntime(agent(false, true), enabledExecutor, history(), summaryService(summary))
                .stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();
        assertThat(enabledExecutor.lastRequest.summaryContext()).isEqualTo("更早的内容摘要");

        var disabledExecutor = new CapturingExecutor();
        newRuntime(agent(false, false), disabledExecutor, history(), summaryService(summary))
                .stream(new AgentRunBo("agent-x", "conv-1", "问题")).collectList().block();
        assertThat(disabledExecutor.lastRequest.summaryContext()).isNull();
    }

    private static List<SessionMessage> history() {
        return List.of(new SessionMessage("conv-1", "agent-x", MessageRole.USER, "你好"),
                new SessionMessage("conv-1", "agent-x", MessageRole.ASSISTANT, "你好！"));
    }

    private static ConversationSummaryService summaryService(ConversationSummaryDTO summary) {
        return new ConversationSummaryService() {
            @Override
            public java.util.Optional<ConversationSummaryDTO> find(String conversationId) {
                return java.util.Optional.of(summary);
            }

            @Override
            public boolean upsertIfNewer(String conversationId, String text, long coveredUntilMessageId,
                                         int coveredMessages, Long modelId) {
                return false;
            }

            @Override
            public boolean invalidateIfCoveringAfter(String conversationId, Long messageId) {
                return false;
            }

            @Override
            public int deleteByAgentKey(String agentKey) {
                return 0;
            }
        };
    }

    private static AgentScopeRuntime newRuntime(AgentDTO agent, AgentTextExecutor executor,
                                                List<SessionMessage> seed,
                                                ConversationSummaryService summaryService) {
        return new AgentScopeRuntime(key -> agent, executor, null, new InMemoryMessageService(seed), null,
                HistoryWindow.unlimited(), summaryService);
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