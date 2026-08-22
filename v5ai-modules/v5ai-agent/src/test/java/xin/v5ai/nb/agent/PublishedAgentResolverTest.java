package xin.v5ai.nb.agent;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentVersion;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishedAgentResolverTest {

    @Test
    void resolveAllowsOnlyPublishedAgent() {
        var repository = new InMemoryAgentRepository();
        var resolver = new PublishedAgentResolver(repository);
        repository.save(new Agent("draft", "Draft", null, AgentStatus.DRAFT, 7L, null));
        repository.save(new Agent("published", "Published", null, AgentStatus.PUBLISHED, 7L, 1L));
        repository.save(new Agent("disabled", "Disabled", null, AgentStatus.DISABLED, 7L, null));

        assertThat(resolver.resolve("published").status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThatThrownBy(() -> resolver.resolve("draft"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("not published");
        assertThatThrownBy(() -> resolver.resolve("missing"))
                .isInstanceOf(AgentPublishException.class)
                .hasMessageContaining("does not exist");
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

    private static final class InMemoryAgentRepository implements AgentService {
        private final List<Agent> agents = new ArrayList<>();

        @Override
        public Agent findByAgentKey(String agentKey) {
            return agents.stream().filter(app -> app.agentKey().equals(agentKey)).findFirst().orElse(null);
        }

        @Override
        public void save(Agent agent) {
            agents.add(agent);
        }

        @Override
        public void update(Agent agent) {
            agents.removeIf(app -> app.agentKey().equals(agent.agentKey()));
            agents.add(agent);
        }

        @Override
        public void delete(String agentKey) {
            agents.removeIf(app -> app.agentKey().equals(agentKey));
        }

        @Override
        public void disable(String agentKey) {
            var current = findByAgentKey(agentKey);
            if (current != null) {
                agents.remove(current);
                agents.add(new Agent(current.agentKey(), current.name(), current.description(),
                        AgentStatus.DISABLED, current.modelId(), current.publishedVersion()));
            }
        }

        @Override
        public List<Agent> list() {
            return agents;
        }

        @Override
        public long nextVersion(String agentKey) {
            return 1L;
        }

        @Override
        public void saveVersion(AgentVersion version) {
        }

        @Override
        public List<AgentVersion> listVersions(String agentKey) {
            return List.of();
        }

        @Override
        public void publish(String agentKey, long version) {
        }
    }
}
