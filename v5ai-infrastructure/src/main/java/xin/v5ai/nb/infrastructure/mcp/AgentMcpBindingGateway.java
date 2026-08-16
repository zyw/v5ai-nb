package xin.v5ai.nb.infrastructure.mcp;

import java.util.List;

public interface AgentMcpBindingGateway {
    void insertBinding(AgentMcpBindingEntity entity);

    void deleteByAgentKey(String agentKey);

    List<AgentMcpBindingEntity> selectByAgentKey(String agentKey);
}
