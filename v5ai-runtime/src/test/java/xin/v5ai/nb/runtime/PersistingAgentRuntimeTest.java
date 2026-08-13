package xin.v5ai.nb.runtime;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class PersistingAgentRuntimeTest {
    @Test
    void completedRunPersistsUserAndAssistantMessages() {
        var messageRepository = new RecordingMessageRepository();
        var runRepository = new RecordingRunRecordRepository();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                messageRepository,
                runRepository
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hello")).then())
                .verifyComplete();

        assertThat(messageRepository.messages)
                .extracting(Message::role)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(messageRepository.messages.get(0).content()).isEqualTo("hello");
        assertThat(messageRepository.messages.get(1).content()).isEqualTo("hi there");
        assertThat(runRepository.completedRunIds).containsExactly("run-1");
    }

    @Test
    void requestWithoutConversationIdGetsGeneratedConversationId() {
        var messageRepository = new RecordingMessageRepository();
        var runtime = new PersistingAgentRuntime(
                new StubAgentRuntime(),
                messageRepository,
                new RecordingRunRecordRepository()
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", null, "hello")).then())
                .verifyComplete();

        assertThat(messageRepository.messages.get(0).conversationId()).isNotBlank();
        assertThat(messageRepository.messages.get(1).conversationId()).isEqualTo(messageRepository.messages.get(0).conversationId());
    }

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

    private static final class RecordingMessageRepository implements MessageRepository {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void save(Message message) {
            messages.add(message);
        }
    }

    private static final class RecordingRunRecordRepository implements RunRecordRepository {
        private final List<String> completedRunIds = new ArrayList<>();

        @Override
        public void start(String runId, AgentRunRequest request) {
        }

        @Override
        public void complete(String runId) {
            completedRunIds.add(runId);
        }
    }
}
