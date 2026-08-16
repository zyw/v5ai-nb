package xin.v5ai.nb.infrastructure.agent;

import java.util.List;

public interface AgentGateway {
    void insertAgent(AgentEntity entity);

    void updateAgent(AgentEntity entity);

    void disable(String agentKey);

    AgentEntity selectByAgentKey(String agentKey);

    List<AgentEntity> selectAgents();

    void updatePublished(String agentKey, String status, Long publishedVersion);
}
