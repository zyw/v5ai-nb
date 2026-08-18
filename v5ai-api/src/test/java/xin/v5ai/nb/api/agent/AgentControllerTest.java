package xin.v5ai.nb.api.agent;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentConfigGenerator;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.agent.AgentVersion;
import xin.v5ai.nb.agent.AgentManagementService;
import xin.v5ai.nb.agent.GeneratedAgentConfig;
import xin.v5ai.nb.common.core.page.PageQuery;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentControllerTest {
    @Test
    void createsListsAndPublishesApplication() {
        var repository = new RecordingAgentRepository();
        var controller = new AgentController(new AgentManagementService(repository, modelId -> modelId == 7L),
                (modelId, description) -> new GeneratedAgentConfig("generated", "gen desc", "hi", List.of("q1"), "prompt"));

        var created = controller.createAgent(new CreateAgentRequest("demo", "Demo", "desc", 7L, "You are a demo agent.",
                "https://example.com/avatar.png", "你好，我是 Demo", "[\"你是谁？\"]", true, true, false, true, false)).getData();
        var listed = controller.listAgents(new PageQuery()).getData().getRows();
        controller.publishAgent("demo", new PublishAgentRequest("release"));

        assertThat(created.agentKey()).isEqualTo("demo");
        assertThat(created.description()).isEqualTo("desc");
        assertThat(created.systemPrompt()).isEqualTo("You are a demo agent.");
        assertThat(created.avatar()).isEqualTo("https://example.com/avatar.png");
        assertThat(created.greeting()).isEqualTo("你好，我是 Demo");
        assertThat(created.presetQuestions()).isEqualTo("[\"你是谁？\"]");
        assertThat(created.memoryEnabled()).isTrue();
        assertThat(created.mcpEnabled()).isTrue();
        assertThat(created.skillEnabled()).isFalse();
        assertThat(created.webSearchEnabled()).isTrue();
        assertThat(created.ragEnabled()).isFalse();
        assertThat(listed).extracting(AgentResponse::agentKey).containsExactly("demo");
        assertThat(repository.findByAgentKey("demo").status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(repository.versions).hasSize(1);
    }

    @Test
    void updatesDisablesAndListsVersions() {
        var repository = new RecordingAgentRepository();
        var controller = new AgentController(new AgentManagementService(repository, modelId -> true),
                (modelId, description) -> new GeneratedAgentConfig("generated", "gen desc", "hi", List.of("q1"), "prompt"));
        controller.createAgent(new CreateAgentRequest("demo", "Demo", null, 7L, null));

        var updated = controller.updateAgent("demo", new UpdateAgentRequest("Demo v2", "new", 8L, null,
                null, "欢迎回来", null, true, null, null, null, null)).getData();
        var versions = controller.listVersions("demo", new PageQuery()).getData().getRows();
        controller.disableAgent("demo");

        assertThat(updated.name()).isEqualTo("Demo v2");
        assertThat(updated.greeting()).isEqualTo("欢迎回来");
        assertThat(updated.memoryEnabled()).isTrue();
        assertThat(updated.mcpEnabled()).isFalse();
        assertThat(updated.ragEnabled()).isFalse();
        assertThat(versions).isEmpty();
        assertThat(repository.findByAgentKey("demo").status()).isEqualTo(AgentStatus.DISABLED);
    }

    @Test
    void generatesAgentConfigFromDescription() {
        var repository = new RecordingAgentRepository();
        var controller = new AgentController(new AgentManagementService(repository, modelId -> true),
                (modelId, description) -> new GeneratedAgentConfig("智能客服", "7x24 客服", "您好，有什么可以帮您？",
                        List.of("怎么退款？", "如何联系人工？"), "You are a customer service agent."));

        var generated = controller.generateAgentConfig(new GenerateAgentConfigRequest(7L, "帮我设计一个智能客服")).getData();

        assertThat(generated.name()).isEqualTo("智能客服");
        assertThat(generated.description()).isEqualTo("7x24 客服");
        assertThat(generated.greeting()).isEqualTo("您好，有什么可以帮您？");
        assertThat(generated.presetQuestions()).containsExactly("怎么退款？", "如何联系人工？");
        assertThat(generated.systemPrompt()).isEqualTo("You are a customer service agent.");
    }

    @Test
    void deletesAgentAndRelatedData() {
        var repository = new RecordingAgentRepository();
        var controller = new AgentController(new AgentManagementService(repository, modelId -> true),
                (modelId, description) -> new GeneratedAgentConfig("generated", "gen desc", "hi", List.of("q1"), "prompt"));
        controller.createAgent(new CreateAgentRequest("demo", "Demo", null, 7L, null));

        controller.deleteAgent("demo");

        assertThat(repository.findByAgentKey("demo")).isNull();
        assertThat(repository.agents).isEmpty();
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

        public void delete(String agentKey) {
            agents.removeIf(app -> app.agentKey().equals(agentKey));
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
