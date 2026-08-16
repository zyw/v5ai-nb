package xin.v5ai.nb.infrastructure.rag;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.AgentKnowledgeBindingRepository;

import java.util.List;

@Repository
public class MyBatisAgentKnowledgeBindingRepository implements AgentKnowledgeBindingRepository {
    private final AgentKnowledgeBindingGateway gateway;

    public MyBatisAgentKnowledgeBindingRepository(AgentKnowledgeBindingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String agentKey, List<Long> knowledgeBaseIds) {
        gateway.deleteByAgentKey(agentKey);
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            var entity = new AgentKnowledgeBindingEntity();
            entity.setAgentKey(agentKey);
            entity.setKnowledgeBaseId(knowledgeBaseId);
            gateway.insertBinding(entity);
        }
    }

    @Override
    public List<Long> findByAgentKey(String agentKey) {
        return gateway.selectByAgentKey(agentKey).stream()
                .map(AgentKnowledgeBindingEntity::getKnowledgeBaseId)
                .toList();
    }

    @Override
    public void delete(String agentKey) {
        gateway.deleteByAgentKey(agentKey);
    }
}
