package xin.v5ai.nb.infrastructure.runtime;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.Message;
import xin.v5ai.nb.runtime.MessageRole;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisRuntimePersistenceTest {
    @Test
    void messageRepositoryMapsRuntimeMessageToV5aiMessageEntity() {
        var mapper = new RecordingMessageMapper();
        var repository = new MyBatisMessageRepository(mapper);

        repository.save(new Message("00000000-0000-0000-0000-000000000001", "demo", MessageRole.USER, "hello"));

        assertThat(mapper.entity.getConversationId()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(mapper.entity.getRole()).isEqualTo("USER");
        assertThat(mapper.entity.getContent()).isEqualTo("hello");
    }

    @Test
    void runRecordRepositoryStoresRunStartAndCompletion() {
        var mapper = new RecordingRunMapper();
        var repository = new MyBatisRunRecordRepository(mapper);
        var request = new AgentRunRequest("demo", "00000000-0000-0000-0000-000000000001", "hello");

        repository.start("10000000-0000-0000-0000-000000000001", request);
        repository.complete("10000000-0000-0000-0000-000000000001");

        assertThat(mapper.started.getStatus()).isEqualTo("RUNNING");
        assertThat(mapper.started.getAppKey()).isEqualTo("demo");
        assertThat(mapper.completedRunId).isEqualTo("10000000-0000-0000-0000-000000000001");
    }

    private static final class RecordingMessageMapper implements RuntimeMessageGateway {
        private RuntimeMessageEntity entity;

        @Override
        public void insertMessage(RuntimeMessageEntity entity) {
            this.entity = entity;
        }
    }

    private static final class RecordingRunMapper implements RuntimeRunGateway {
        private RuntimeRunEntity started;
        private String completedRunId;

        @Override
        public void insertRun(RuntimeRunEntity entity) {
            started = entity;
        }

        @Override
        public void markCompleted(String runId) {
            completedRunId = runId;
        }
    }
}
