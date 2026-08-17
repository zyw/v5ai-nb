package xin.v5ai.nb.runtime;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xin.v5ai.nb.common.core.platform.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersistingAgentRuntimeTest {
    @Test
    void completedRunPersistsUserAndAssistantMessages() {
        var messageRepository = new RecordingMessageRepository();
        var runRepository = new RecordingRunRecordRepository();
        var conversationRepository = new RecordingConversationRepository();
        var runEventRepository = new RecordingRunEventRepository();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                messageRepository,
                runRepository,
                conversationRepository,
                runEventRepository
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")).then())
                .verifyComplete();

        assertThat(messageRepository.messages)
                .extracting(Message::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(messageRepository.messages.get(0).content()).isEqualTo("hello");
        assertThat(messageRepository.messages.get(1).content()).isEqualTo("hi there");
        assertThat(runRepository.completedRunIds).containsExactly("run-1");
        assertThat(conversationRepository.conversations).extracting(Conversation::conversationId)
                .containsExactly("conversation-1");
        assertThat(runEventRepository.events).extracting(RuntimeEvent::type)
                .containsExactly(RuntimeEventType.RUN_STARTED, RuntimeEventType.TEXT_DELTA,
                        RuntimeEventType.TEXT_DELTA, RuntimeEventType.RUN_COMPLETED);
    }

    @Test
    void requestWithoutConversationIdGetsGeneratedConversationId() {
        var messageRepository = new RecordingMessageRepository();
        var conversationRepository = new RecordingConversationRepository();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                messageRepository,
                new RecordingRunRecordRepository(),
                conversationRepository,
                new RecordingRunEventRepository()
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", null, "hello")).then())
                .verifyComplete();

        assertThat(messageRepository.messages.get(0).conversationId()).isNotBlank();
        assertThat(messageRepository.messages.get(1).conversationId()).isEqualTo(messageRepository.messages.get(0).conversationId());
        assertThat(conversationRepository.conversations.getFirst().conversationId())
                .isEqualTo(messageRepository.messages.get(0).conversationId());
    }

    @Test
    void failedRunPersistsFailedStatusAndRunFailedEvent() {
        var messageRepository = new RecordingMessageRepository();
        var runRepository = new RecordingRunRecordRepository();
        var runEventRepository = new RecordingRunEventRepository();
        var runtime = new PersistingAgentRuntime(
                new FailingAgentRuntime(),
                messageRepository,
                runRepository,
                new RecordingConversationRepository(),
                runEventRepository
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_FAILED)
                .verifyComplete();

        assertThat(runRepository.failedRunIds).hasSize(1);
        assertThat(runRepository.failedMessages.getFirst()).contains("boom");
        assertThat(runEventRepository.events).extracting(RuntimeEvent::type)
                .contains(RuntimeEventType.RUN_FAILED);
    }

    @Test
    void completedRunPersistsAgentStateWhenRepositoryPresent() {
        var stateRepository = new RecordingAgentStateRepository();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                new RecordingMessageRepository(),
                new RecordingRunRecordRepository(),
                new RecordingConversationRepository(),
                new RecordingRunEventRepository(),
                stateRepository
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")).then())
                .verifyComplete();

        assertThat(stateRepository.savedConversationId).isEqualTo("conversation-1");
        assertThat(stateRepository.savedStateJson).contains("\"agentKey\":\"demo\"");
    }

    @Test
    void runRecordIsStartedBeforeItsFirstEventIsPersisted() {
        var runRepository = new RecordingRunRecordRepository();
        var runEventRepository = new RecordingRunEventRepository();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                new RecordingMessageRepository(),
                runRepository,
                new RecordingConversationRepository(),
                runEventRepository
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")).then())
                .verifyComplete();

        // v5ai_run_event.run_id references v5ai_run.id: the run row must exist first
        assertThat(runRepository.startOrder).isGreaterThan(0);
        assertThat(runEventRepository.firstSaveOrder).isGreaterThan(runRepository.startOrder);
    }

    @Test
    void errorBeforeRunStartedStillPersistsRunRowBeforeFailedEvent() {
        var runRepository = new RecordingRunRecordRepository();
        var runEventRepository = new RecordingRunEventRepository();
        var runtime = new PersistingAgentRuntime(
                new FailingAgentRuntime(),
                new RecordingMessageRepository(),
                runRepository,
                new RecordingConversationRepository(),
                runEventRepository
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_FAILED)
                .verifyComplete();

        assertThat(runRepository.startedRunIds).hasSize(1);
        assertThat(runRepository.failedRunIds).containsExactly(runRepository.startedRunIds.getFirst());
        assertThat(runEventRepository.firstSaveOrder).isGreaterThan(runRepository.startOrder);
    }

    private static final java.util.concurrent.atomic.AtomicInteger ORDER =
            new java.util.concurrent.atomic.AtomicInteger();

    private static final class StubAgentRuntime implements AgentRuntime {
        @Override
        public Flux<RuntimeEvent> stream(AgentRunRequest request) {
            return Flux.just(
                    RuntimeEvent.started("run-1"),
                    RuntimeEvent.textDelta("run-1", "hi"),
                    RuntimeEvent.textDelta("run-1", " there"),
                    RuntimeEvent.completed("run-1")
            );
        }

        @Override
        public Mono<AgentRunResult> call(AgentRunRequest request) {
            return Mono.just(new AgentRunResult("run-1", "hi there"));
        }
    }

    private static final class FailingAgentRuntime implements AgentRuntime {
        @Override
        public Flux<RuntimeEvent> stream(AgentRunRequest request) {
            return Flux.error(new IllegalStateException("boom"));
        }

        @Override
        public Mono<AgentRunResult> call(AgentRunRequest request) {
            return Mono.error(new IllegalStateException("boom"));
        }
    }

    private static final class RecordingMessageRepository implements MessageRepository {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void save(Message message) {
            messages.add(message);
        }

        @Override
        public List<Message> findByConversationId(String conversationId) {
            return messages.stream().filter(message -> message.conversationId().equals(conversationId)).toList();
        }
    }

    private static final class RecordingRunRecordRepository implements RunRecordRepository {
        private final List<String> completedRunIds = new ArrayList<>();
        private final List<String> failedRunIds = new ArrayList<>();
        private final List<String> failedMessages = new ArrayList<>();
        private final List<String> startedRunIds = new ArrayList<>();
        private int startOrder = -1;

        @Override
        public void start(String runId, AgentRunRequest request) {
            startedRunIds.add(runId);
            startOrder = ORDER.incrementAndGet();
        }

        @Override
        public void complete(String runId) {
            completedRunIds.add(runId);
        }

        @Override
        public void fail(String runId, String message) {
            failedRunIds.add(runId);
            failedMessages.add(message);
        }
    }

    private static final class RecordingConversationRepository implements ConversationRepository {
        private final List<Conversation> conversations = new ArrayList<>();

        @Override
        public void ensureConversation(Conversation conversation) {
            conversations.add(conversation);
        }
    }

    private static final class RecordingRunEventRepository implements RunEventRepository {
        private final List<RuntimeEvent> events = new ArrayList<>();
        private int firstSaveOrder = -1;

        @Override
        public void save(RuntimeEvent event) {
            if (firstSaveOrder < 0) {
                firstSaveOrder = ORDER.incrementAndGet();
            }
            events.add(event);
        }
    }

    private static final class RecordingAgentStateRepository implements AgentStateRepository {
        private String savedConversationId;
        private String savedStateJson;

        @Override
        public void save(String conversationId, String stateJson) {
            this.savedConversationId = conversationId;
            this.savedStateJson = stateJson;
        }

        @Override
        public String load(String conversationId) {
            return savedStateJson;
        }
    }

    @Test
    void completedRunRecordsUsageAndQuota() {
        var usageRecorder = new RecordingUsageRecorder();
        var quotaService = new RecordingQuotaService();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                new RecordingMessageRepository(),
                new RecordingRunRecordRepository(),
                new RecordingConversationRepository(),
                new RecordingRunEventRepository(),
                null,
                usageRecorder,
                quotaService
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")).then())
                .verifyComplete();

        assertThat(usageRecorder.records).hasSize(1);
        assertThat(usageRecorder.records.get(0).agentKey()).isEqualTo("demo");
        assertThat(usageRecorder.records.get(0).status()).isEqualTo("SUCCESS");
        assertThat(usageRecorder.records.get(0).totalTokens()).isGreaterThan(0);
        assertThat(quotaService.recordedAgentKeys).containsExactly("demo");
        assertThat(quotaService.recordedTokens).isGreaterThan(0);
    }

    @Test
    void failedRunRecordsFailedUsage() {
        var usageRecorder = new RecordingUsageRecorder();
        var runtime = new PersistingAgentRuntime(
                new FailingAgentRuntime(),
                new RecordingMessageRepository(),
                new RecordingRunRecordRepository(),
                new RecordingConversationRepository(),
                new RecordingRunEventRepository(),
                null,
                usageRecorder,
                new RecordingQuotaService()
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")).then())
                .verifyComplete();

        assertThat(usageRecorder.records).hasSize(1);
        assertThat(usageRecorder.records.get(0).status()).isEqualTo("FAILED");
    }

    private static final class RecordingUsageRecorder implements UsageRecorder {
        private final List<UsageRecord> records = new ArrayList<>();

        @Override
        public void record(UsageRecord usage) {
            records.add(usage);
        }
    }

    private static final class RecordingQuotaService implements AppQuotaService {
        private final List<String> recordedAgentKeys = new ArrayList<>();
        private long recordedTokens;

        @Override
        public AppQuota getQuota(String agentKey) {
            return new AppQuota(agentKey, 0, 0, 0, null);
        }

        @Override
        public AppQuota updateQuota(String agentKey, Integer dailyModelCalls,
                                                                Long dailyTokens, Integer ratePerMinute) {
            return getQuota(agentKey);
        }

        @Override
        public boolean checkAllowed(String agentKey) {
            return true;
        }

        @Override
        public void recordUsage(String agentKey, long tokens) {
            recordedAgentKeys.add(agentKey);
            recordedTokens += tokens;
        }

        @Override
        public AppUsage getDailyUsage(String agentKey) {
            return new AppUsage(agentKey, java.time.LocalDate.now(),
                    recordedAgentKeys.size(), recordedTokens);
        }
    }
}
