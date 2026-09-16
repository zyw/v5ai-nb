package xin.v5ai.nb.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.agent.service.impl.InMemoryAgentServiceImpl;
import xin.v5ai.nb.common.agentscope.core.resolver.PublishedAgentResolver;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;

@Configuration
public class AgentRuntimeConfiguration {

    @Value("${v5ai.runtime.dev-app-key:demo}")
    private String agentKey;

    @Bean
    AgentService agentRepository() {
        var repository = new InMemoryAgentServiceImpl();
        repository.save(new AgentDTO(agentKey, "Demo", "Development demo application", AgentStatus.PUBLISHED, 1L, 1L));
        return repository;
    }

    @Bean
    PublishedAgentResolver publishedAgentResolver(AgentService agentService) {
        return new PublishedAgentResolver(agentService);
    }
}
