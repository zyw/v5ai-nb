package xin.v5ai.nb.infrastructure.agent;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.agentscope.core.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.domain.AgentVersion;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;

@Configuration
public class AgentRuntimeConfiguration {
    @Bean
    AgentService agentService(
            @Value("${v5ai.runtime.dev-app-key:demo}") String agentKey
    ) {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent(agentKey, "Demo", "Development demo application", AgentStatus.PUBLISHED, 1L, 1L));
        return repository;
    }

    @Bean
    PublishedAgentResolver publishedAgentResolver(AgentService agentRepository) {
        return new PublishedAgentResolver(agentRepository);
    }

    private static final class InMemoryAgentRepository implements AgentService {
        private final Map<String, Agent> agents = new ConcurrentHashMap<>();
        private final List<AgentVersion> versions = new ArrayList<>();

        @Override
        public Agent findByAgentKey(String agentKey) {
            return agents.get(agentKey);
        }

        @Override
        public void save(Agent application) {
            agents.put(application.agentKey(), application);
        }

        @Override
        public void update(Agent application) {
            agents.put(application.agentKey(), application);
        }

        @Override

        public void delete(String agentKey) {

            agents.remove(agentKey);

        }


        @Override
        public void disable(String agentKey) {
            var current = agents.get(agentKey);
            if (current != null) {
                agents.put(agentKey, new Agent(current.agentKey(), current.name(), current.description(),
                        AgentStatus.DISABLED, current.modelId(), current.publishedVersion()));
            }
        }

        @Override
        public List<Agent> list() {
            return new ArrayList<>(agents.values());
        }

        @Override
        public long nextVersion(String agentKey) {
            return versions.stream().filter(version -> version.agentKey().equals(agentKey)).count() + 1;
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
            var current = agents.get(agentKey);
            agents.put(agentKey, new Agent(current.agentKey(), current.name(), current.description(),
                    AgentStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
