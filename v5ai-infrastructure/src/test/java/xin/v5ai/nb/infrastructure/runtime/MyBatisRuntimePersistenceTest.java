package xin.v5ai.nb.infrastructure.runtime;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.Conversation;
import xin.v5ai.nb.runtime.Message;
import xin.v5ai.nb.runtime.MessageRole;
import xin.v5ai.nb.runtime.RuntimeEvent;
import xin.v5ai.nb.runtime.RuntimeEventType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisRuntimePersistenceTest {
    @Test
    void messageRepositoryMapsRuntimeMessageToV5aiMessageEntity() {
        var mapper = new RecordingMessageMapper();
        var repository = new MyBatisMessageRepository(mapper);

        repository.save(new Message("00000000-0000-0000-0000-000000000001", "demo", MessageRole.USER, "hello"));

        assertThat(mapper.entity.getConversationId()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(mapper.entity.getAppKey()).isEqualTo("demo");
        assertThat(mapper.entity.getRole()).isEqualTo("USER");
        assertThat(mapper.entity.getContent()).isEqualTo("hello");
    }

    @Test
    void messageRepositoryFindsMessagesByConversationId() {
        var mapper = new RecordingMessageMapper();
        mapper.messages.add(entity("00000000-0000-0000-0000-000000000001", "demo", "USER", "hello"));
        var repository = new MyBatisMessageRepository(mapper);

        var messages = repository.findByConversationId("00000000-0000-0000-0000-000000000001");

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().role()).isEqualTo(MessageRole.USER);
        assertThat(messages.getFirst().appKey()).isEqualTo("demo");
    }

    @Test
    void runRecordRepositoryStoresRunStartCompletionAndFailure() {
        var mapper = new RecordingRunMapper();
        var repository = new MyBatisRunRecordRepository(mapper);
        var request = new AgentRunRequest("demo", "00000000-0000-0000-0000-000000000001", "hello");

        repository.start("10000000-0000-0000-0000-000000000001", request);
        repository.complete("10000000-0000-0000-0000-000000000001");
        repository.fail("20000000-0000-0000-0000-000000000001", "boom");

        assertThat(mapper.started.getStatus()).isEqualTo("RUNNING");
        assertThat(mapper.started.getAppKey()).isEqualTo("demo");
        assertThat(mapper.completedRunId).isEqualTo("10000000-0000-0000-0000-000000000001");
        assertThat(mapper.failedRunId).isEqualTo("20000000-0000-0000-0000-000000000001");
        assertThat(mapper.failedMessage).isEqualTo("boom");
    }

    @Test
    void agentStateRepositoryUpsertsAndLoadsState() {
        var mapper = new RecordingAgentStateMapper();
        var repository = new MyBatisAgentStateRepository(mapper);

        repository.save("conversation-1", "{\"state\":1}");
        repository.save("conversation-1", "{\"state\":2}");

        assertThat(repository.load("conversation-1")).isEqualTo("{\"state\":2}");
    }

    @Test
    void conversationRepositoryEnsuresConversation() {
        var mapper = new RecordingConversationMapper();
        var repository = new MyBatisConversationRepository(mapper);

        repository.ensureConversation(new Conversation("conversation-1", "demo"));

        assertThat(mapper.entity.getConversationId()).isEqualTo("conversation-1");
        assertThat(mapper.entity.getAppKey()).isEqualTo("demo");
    }

    @Test
    void runEventRepositoryStoresRuntimeEvent() {
        var mapper = new RecordingRunEventMapper();
        var repository = new MyBatisRunEventRepository(mapper);

        repository.save(new RuntimeEvent("run-1", RuntimeEventType.TEXT_DELTA, "hello", java.time.Instant.EPOCH));

        assertThat(mapper.entity.getRunId()).isEqualTo("run-1");
        assertThat(mapper.entity.getEventType()).isEqualTo("TEXT_DELTA");
        assertThat(mapper.entity.getPayload()).isEqualTo("hello");
    }

    private static RuntimeMessageEntity entity(String conversationId, String appKey, String role, String content) {
        var entity = new RuntimeMessageEntity();
        entity.setConversationId(conversationId);
        entity.setAppKey(appKey);
        entity.setRole(role);
        entity.setContent(content);
        return entity;
    }

    private static final class RecordingMessageMapper implements RuntimeMessageGateway {
        private RuntimeMessageEntity entity;
        private final List<RuntimeMessageEntity> messages = new ArrayList<>();

        @Override
        public void insertMessage(RuntimeMessageEntity entity) {
            this.entity = entity;
            messages.add(entity);
        }

        @Override
        public List<RuntimeMessageEntity> selectMessagesByConversationId(String conversationId) {
            return messages.stream().filter(message -> message.getConversationId().equals(conversationId)).toList();
        }
    }

    private static final class RecordingRunMapper implements RuntimeRunGateway {
        private RuntimeRunEntity started;
        private String completedRunId;
        private String failedRunId;
        private String failedMessage;

        @Override
        public void insertRun(RuntimeRunEntity entity) {
            started = entity;
        }

        @Override
        public void markCompleted(String runId) {
            completedRunId = runId;
        }

        @Override
        public void markFailed(String runId, String errorMessage) {
            failedRunId = runId;
            failedMessage = errorMessage;
        }
    }

    private static final class RecordingAgentStateMapper implements AgentStateGateway {
        private AgentStateEntity stored;

        @Override
        public void upsert(AgentStateEntity entity) {
            stored = entity;
        }

        @Override
        public AgentStateEntity selectByConversationId(String conversationId) {
            return stored;
        }
    }

    private static final class RecordingConversationMapper implements RuntimeConversationGateway {
        private RuntimeConversationEntity entity;

        @Override
        public void upsertConversation(RuntimeConversationEntity entity) {
            this.entity = entity;
        }
    }

    private static final class RecordingRunEventMapper implements RuntimeRunEventGateway {
        private RuntimeRunEventEntity entity;

        @Override
        public void insertRunEvent(RuntimeRunEventEntity entity) {
            this.entity = entity;
        }
    }
}
