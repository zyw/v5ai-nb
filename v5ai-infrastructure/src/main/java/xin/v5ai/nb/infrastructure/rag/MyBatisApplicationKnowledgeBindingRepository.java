package xin.v5ai.nb.infrastructure.rag;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.ApplicationKnowledgeBindingRepository;

import java.util.List;

@Repository
public class MyBatisApplicationKnowledgeBindingRepository implements ApplicationKnowledgeBindingRepository {
    private final ApplicationKnowledgeBindingGateway gateway;

    public MyBatisApplicationKnowledgeBindingRepository(ApplicationKnowledgeBindingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String appKey, List<Long> knowledgeBaseIds) {
        gateway.deleteByAppKey(appKey);
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            var entity = new ApplicationKnowledgeBindingEntity();
            entity.setAppKey(appKey);
            entity.setKnowledgeBaseId(knowledgeBaseId);
            gateway.insertBinding(entity);
        }
    }

    @Override
    public List<Long> findByAppKey(String appKey) {
        return gateway.selectByAppKey(appKey).stream()
                .map(ApplicationKnowledgeBindingEntity::getKnowledgeBaseId)
                .toList();
    }

    @Override
    public void delete(String appKey) {
        gateway.deleteByAppKey(appKey);
    }
}
