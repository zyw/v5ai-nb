package xin.v5ai.nb.api.agent;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.agent.AgentVersion;
import xin.v5ai.nb.agent.AgentManagementService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentControllerTest {
    @Test
    void createsListsAndPublishesApplication() {
        var repository = new RecordingAgentRepository();
        var controller = new AgentController(new AgentManagementService(repository, modelId -> modelId == 7L));

        var created = controller.createAgent(new CreateAgentRequest("demo", "Demo", "desc", 7L, "You are a demo agent."));
        var listed = controller.listAgents();
        controller.publishAgent("demo", new PublishAgentRequest("release"));

        assertThat(created.agentKey()).isEqualTo("demo");
        assertThat(created.description()).isEqualTo("desc");
        assertThat(created.systemPrompt()).isEqualTo("You are a demo agent.");
        assertThat(listed).extracting(AgentResponse::agentKey).containsExactly("demo");
        assertThat(repository.findByAgentKey("demo").status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(repository.versions).hasSize(1);
    }

    @Test
    void updatesDisablesAndListsVersions() {
        var repository = new RecordingAgentRepository();
        var controller = new AgentController(new AgentManagementService(repository, modelId -> true));
        controller.createAgent(new CreateAgentRequest("demo", "Demo", null, 7L, null));

        var updated = controller.updateAgent("demo", new UpdateAgentRequest("Demo v2", "new", 8L, null));
        var versions = controller.listVersions("demo");
        controller.disableAgent("demo");

        assertThat(updated.name()).isEqualTo("Demo v2");
        assertThat(versions).isEmpty();
        assertThat(repository.findByAgentKey("demo").status()).isEqualTo(AgentStatus.DISABLED);
    }

    private static final class RecordingAgentRepository implements AgentRepository {
        private final List<Agent> agents = new ArrayList<>();
        private final List<AgentVersion> versions = new ArrayList<>();

        @Override
        public Agent findByAgentKey(String agentKey) {
            return agents.stream().filter(app -> app.agentKey().equals(agentKey)).findFirst().orElse(null);
        }

        @Override
        public void save(Agent application) {
            agents.add(application);
        }

        @Override
        public void update(Agent application) {
            agents.removeIf(app -> app.agentKey().equals(application.agentKey()));
            agents.add(application);
        }

        @Override
        public void disable(String agentKey) {
            var current = findByAgentKey(agentKey);
            agents.remove(current);
            agents.add(new Agent(current.agentKey(), current.name(), current.description(),
                    AgentStatus.DISABLED, current.modelId(), current.publishedVersion()));
        }

        @Override
        public List<Agent> list() {
            return agents;
        }

        @Override
        public long nextVersion(String agentKey) {
            return versions.size() + 1L;
        }

        @Override
        public void saveVersion(AgentVersion version) {
            versions.add(version);
        }

        @Override
        public List<AgentVersion> listVersions(String agentKey) {
            return versions.stream().filter(version -> version.agentKey().equals(agentKey)).toList();
        }

        @Override
        public void publish(String agentKey, long version) {
            var current = findByAgentKey(agentKey);
            agents.remove(current);
            agents.add(new Agent(agentKey, current.name(), current.description(), AgentStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
