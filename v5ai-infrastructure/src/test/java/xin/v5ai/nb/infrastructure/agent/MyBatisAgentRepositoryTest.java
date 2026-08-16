package xin.v5ai.nb.infrastructure.agent;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.agent.AgentVersion;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisAgentRepositoryTest {
    @Test
    void savesAndFindsApplication() {
        var appMapper = new RecordingAgentMapper();
        var versionMapper = new RecordingAgentVersionMapper();
        var repository = new MyBatisAgentRepository(appMapper, versionMapper);

        repository.save(new Agent("demo", "Demo", "description", AgentStatus.DRAFT, 7L, null));

        Agent application = repository.findByAgentKey("demo");

        assertThat(application.agentKey()).isEqualTo("demo");
        assertThat(application.description()).isEqualTo("description");
        assertThat(application.modelId()).isEqualTo(7L);
        assertThat(application.status()).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    void storesPublishedVersionSnapshotAndPublishesApp() {
        var appMapper = new RecordingAgentMapper();
        var versionMapper = new RecordingAgentVersionMapper();
        var repository = new MyBatisAgentRepository(appMapper, versionMapper);
        repository.save(new Agent("demo", "Demo", null, AgentStatus.DRAFT, 7L, null));

        long next = repository.nextVersion("demo");
        repository.saveVersion(new AgentVersion("demo", next, "{\"agentKey\":\"demo\"}", "release"));
        repository.publish("demo", next);

        assertThat(next).isEqualTo(1L);
        assertThat(versionMapper.versions).hasSize(1);
        assertThat(repository.findByAgentKey("demo").status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(repository.findByAgentKey("demo").publishedVersion()).isEqualTo(1L);
    }

    @Test
    void updateAndDisableChangeTheApplicationRow() {
        var appMapper = new RecordingAgentMapper();
        var repository = new MyBatisAgentRepository(appMapper, new RecordingAgentVersionMapper());
        repository.save(new Agent("demo", "Demo", null, AgentStatus.DRAFT, 7L, null));

        repository.update(new Agent("demo", "Demo v2", "desc", AgentStatus.DRAFT, 8L, null));
        repository.disable("demo");

        var entity = appMapper.selectByAgentKey("demo");
        assertThat(entity.getName()).isEqualTo("Demo v2");
        assertThat(entity.getDescription()).isEqualTo("desc");
        assertThat(entity.getModelId()).isEqualTo(8L);
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void listVersionsReturnsSavedVersionsInDescendingOrder() {
        var versionMapper = new RecordingAgentVersionMapper();
        var repository = new MyBatisAgentRepository(new RecordingAgentMapper(), versionMapper);
        repository.saveVersion(new AgentVersion("demo", 1L, "{}", "first"));
        repository.saveVersion(new AgentVersion("demo", 2L, "{}", "second"));

        assertThat(repository.listVersions("demo"))
                .extracting(AgentVersion::version)
                .containsExactly(2L, 1L);
    }

    private static final class RecordingAgentMapper implements AgentGateway {
        private final List<AgentEntity> agents = new ArrayList<>();

        @Override
        public void insertAgent(AgentEntity entity) {
            agents.add(entity);
        }

        @Override
        public void updateAgent(AgentEntity entity) {
            var index = agents.indexOf(selectByAgentKey(entity.getAgentKey()));
            agents.set(index, entity);
        }

        @Override
        public void disable(String agentKey) {
            var entity = selectByAgentKey(agentKey);
            entity.setStatus("DISABLED");
        }

        @Override
        public AgentEntity selectByAgentKey(String agentKey) {
            return agents.stream()
                    .filter(entity -> entity.getAgentKey().equals(agentKey))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<AgentEntity> selectAgents() {
            return agents;
        }

        @Override
        public void updatePublished(String agentKey, String status, Long publishedVersion) {
            var entity = selectByAgentKey(agentKey);
            entity.setStatus(status);
            entity.setPublishedVersion(publishedVersion);
        }
    }

    private static final class RecordingAgentVersionMapper implements AgentVersionGateway {
        private final List<AgentVersionEntity> versions = new ArrayList<>();

        @Override
        public void insertVersion(AgentVersionEntity entity) {
            versions.add(entity);
        }

        @Override
        public long countVersions(String agentKey) {
            return versions.stream().filter(version -> version.getAgentKey().equals(agentKey)).count();
        }

        @Override
        public List<AgentVersionEntity> selectVersions(String agentKey) {
            return versions.stream()
                    .filter(version -> version.getAgentKey().equals(agentKey))
                    .sorted((a, b) -> Long.compare(b.getVersion(), a.getVersion()))
                    .toList();
        }
    }
}
