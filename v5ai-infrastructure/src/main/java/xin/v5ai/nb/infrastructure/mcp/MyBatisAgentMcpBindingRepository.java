package xin.v5ai.nb.infrastructure.mcp;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.mcp.AgentMcpBindingRepository;

import java.util.List;

@Repository
public class MyBatisAgentMcpBindingRepository implements AgentMcpBindingRepository {
    private final AgentMcpBindingGateway gateway;

    public MyBatisAgentMcpBindingRepository(AgentMcpBindingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String agentKey, List<Long> mcpServerIds) {
        gateway.deleteByAgentKey(agentKey);
        for (Long serverId : mcpServerIds) {
            var entity = new AgentMcpBindingEntity();
            entity.setAgentKey(agentKey);
            entity.setMcpServerId(serverId);
            gateway.insertBinding(entity);
        }
    }

    @Override
    public List<Long> findByAgentKey(String agentKey) {
        return gateway.selectByAgentKey(agentKey).stream()
                .map(AgentMcpBindingEntity::getMcpServerId)
                .toList();
    }

    @Override
    public void delete(String agentKey) {
        gateway.deleteByAgentKey(agentKey);
    }
}
