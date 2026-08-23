package xin.v5ai.nb.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.agent.core.InMemoryAgentRepository;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.core.domain.Agent;
import xin.v5ai.nb.common.agentscope.core.repository.AgentRepository;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;

@Configuration
public class AgentRuntimeConfiguration {

    @Value("${v5ai.runtime.dev-app-key:demo}")
    private String agentKey;

    @Bean
    AgentRepository agentRepository() {
        var repository = new InMemoryAgentRepository();
        repository.save(new Agent(agentKey, "Demo", "Development demo application", AgentStatus.PUBLISHED, 1L, 1L));
        return repository;
    }

    @Bean
    PublishedAgentResolver publishedAgentResolver(AgentRepository agentRepository) {
        return new PublishedAgentResolver(agentRepository);
    }
}
