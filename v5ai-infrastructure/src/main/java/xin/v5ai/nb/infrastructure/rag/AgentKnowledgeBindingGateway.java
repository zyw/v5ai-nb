package xin.v5ai.nb.infrastructure.rag;

import java.util.List;

public interface AgentKnowledgeBindingGateway {
    void insertBinding(AgentKnowledgeBindingEntity entity);

    void deleteByAgentKey(String agentKey);

    List<AgentKnowledgeBindingEntity> selectByAgentKey(String agentKey);
}
