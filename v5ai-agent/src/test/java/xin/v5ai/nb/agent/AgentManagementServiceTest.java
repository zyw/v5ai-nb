package xin.v5ai.nb.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentManagementServiceTest {
    @Test
    void createsDraftApplicationWithModel() {
        var repository = new InMemoryAgentRepository();
        var service = new AgentManagementService(repository, modelId -> true);

        var agent = service.createAgent(new CreateAgentCommand("demo", "Demo", 7L));

        assertThat(agent.agentKey()).isEqualTo("demo");
        assertThat(agent.status()).isEqualTo(AgentStatus.DRAFT);
        assertThat(repository.findByAgentKey("demo")).isEqualTo(agent);
    }

    @Test
    void duplicateAgentKeyIsRejected() {
        var repository = new InMemoryAgentRepository();
        var service = new AgentManagementService(repository, modelId -> true);
        service.createAgent(new CreateAgentCommand("demo", "Demo", 7L));

        assertThatThrownBy(() -> service.createAgent(new CreateAgentCommand("demo", "Other", 8L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void listsApplications() {
        var repository = new InMemoryAgentRepository();
        var service = new AgentManagementService(repository, modelId -> true);
        service.createAgent(new CreateAgentCommand("demo", "Demo", 7L));

        assertThat(service.listAgents())
                .extracting(Agent::agentKey)
                .containsExactly("demo");
    }

    @Test
    void updateAgentKeepsStatusAndChangesNameAndModel() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent("demo", "Demo", "old description", AgentStatus.DRAFT, 7L, null));
        var service = new AgentManagementService(repository, modelId -> true);

        var updated = service.updateAgent(new UpdateAgentCommand("demo", "Demo v2", "new description", 8L));

        assertThat(updated.name()).isEqualTo("Demo v2");
        assertThat(updated.description()).isEqualTo("new description");
        assertThat(updated.modelId()).isEqualTo(8L);
        assertThat(updated.status()).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    void disableApplicationSetsDisabledStatus() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L));
        var service = new AgentManagementService(repository, modelId -> true);

        service.disable("demo");

        assertThat(repository.findByAgentKey("demo").status()).isEqualTo(AgentStatus.DISABLED);
    }

    @Test
    void listVersionsReturnsSavedVersions() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L));
        repository.saveVersion(new AgentVersion("demo", 1L, "{}", "first"));
        var service = new AgentManagementService(repository, modelId -> true);

        assertThat(service.listVersions("demo")).extracting(AgentVersion::version).containsExactly(1L);
    }

    @Test
    void publishingApplicationWithoutModelIsRejected() {
        var service = new AgentManagementService(new InMemoryAgentRepository(), modelId -> false);

        assertThatThrownBy(() -> service.publish("demo"))
                .isInstanceOf(AgentPublishException.class);
    }

    @Test
    void createAgentCarriesDisplayAndFeatureFields() {
        var repository = new InMemoryAgentRepository();
        var service = new AgentManagementService(repository, modelId -> true);

        var agent = service.createAgent(new CreateAgentCommand("demo", "Demo", "desc", 7L, "prompt",
                "https://example.com/avatar.png", "你好", "[\"q1\"]", true, true, true, true, false));

        assertThat(agent.avatar()).isEqualTo("https://example.com/avatar.png");
        assertThat(agent.greeting()).isEqualTo("你好");
        assertThat(agent.presetQuestions()).isEqualTo("[\"q1\"]");
        assertThat(agent.memoryEnabled()).isTrue();
        assertThat(agent.mcpEnabled()).isTrue();
        assertThat(agent.skillEnabled()).isTrue();
        assertThat(agent.webSearchEnabled()).isTrue();
        assertThat(agent.ragEnabled()).isFalse();
    }

    @Test
    void updateAgentKeepsUnspecifiedFeatureFlags() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent("demo", "Demo", null, AgentStatus.DRAFT, 7L, null));
        var service = new AgentManagementService(repository, modelId -> true);

        var updated = service.updateAgent(new UpdateAgentCommand("demo", "Demo v2", null, null, null,
                null, "欢迎语", null, null, null, null, null, true));

        assertThat(updated.greeting()).isEqualTo("欢迎语");
        assertThat(updated.ragEnabled()).isTrue();
        assertThat(updated.memoryEnabled()).isFalse();
        assertThat(updated.mcpEnabled()).isFalse();
    }

    @Test
    void resolveForDebugAllowsDraftAndRejectsDisabled() {
        var repository = new InMemoryAgentRepository();
        var resolver = new PublishedAgentResolver(repository);
        repository.save(new Agent("draft", "Draft", null, AgentStatus.DRAFT, 7L, null));
        repository.save(new Agent("disabled", "Disabled", null, AgentStatus.DISABLED, 7L, null));

        assertThat(resolver.resolveForDebug("draft").status()).isEqualTo(AgentStatus.DRAFT);
        assertThatThrownBy(() -> resolver.resolveForDebug("disabled"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("disabled");
        assertThatThrownBy(() -> resolver.resolveForDebug("missing"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void publishingApplicationUsesModelIdAndPublishesVersion() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent("demo", "Demo", null, AgentStatus.DRAFT, 7L, null));
        var service = new AgentManagementService(repository, modelId -> modelId == 7L);

        service.publish(new PublishAgentCommand("demo", "first release"));

        var agent = repository.findByAgentKey("demo");
        assertThat(agent.status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(agent.publishedVersion()).isEqualTo(1L);
        assertThat(repository.versions).hasSize(1);
        assertThat(repository.versions.getFirst().snapshotJson())
                .contains("\"agentKey\":\"demo\"")
                .contains("\"modelId\":7");
    }

    @Test
    void publishedApplicationCanBeResolved() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L));
        var resolver = new PublishedAgentResolver(repository);

        var agent = resolver.resolve("demo");

        assertThat(agent.status()).isEqualTo(AgentStatus.PUBLISHED);
    }

    private static final class InMemoryAgentRepository implements AgentRepository {
        private final java.util.Map<String, Agent> agents = new java.util.HashMap<>();
        private final List<AgentVersion> versions = new ArrayList<>();

        @Override public Agent findByAgentKey(String agentKey) { return agents.get(agentKey); }
        @Override public void save(Agent agent) { agents.put(agent.agentKey(), agent); }
        @Override public void update(Agent agent) { agents.put(agent.agentKey(), agent); }
        @Override public void disable(String agentKey) {
            var current = agents.get(agentKey);
            if (current != null) {
                agents.put(agentKey, new Agent(current.agentKey(), current.name(), current.description(),
                        AgentStatus.DISABLED, current.modelId(), current.publishedVersion()));
            }
        }
        @Override public List<Agent> list() { return new ArrayList<>(agents.values()); }
        @Override public long nextVersion(String agentKey) { return versions.stream().filter(v -> v.agentKey().equals(agentKey)).count() + 1; }
        @Override public void saveVersion(AgentVersion version) { versions.add(version); }
        @Override public List<AgentVersion> listVersions(String agentKey) {
            return versions.stream().filter(v -> v.agentKey().equals(agentKey)).toList();
        }
        @Override public void publish(String agentKey, long version) {
            var current = agents.get(agentKey);
            agents.put(agentKey, new Agent(current.agentKey(), current.name(), current.description(),
                    AgentStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
