package xin.v5ai.nb.infrastructure.rag;

import java.util.List;

public interface ApplicationKnowledgeBindingGateway {
    void insertBinding(ApplicationKnowledgeBindingEntity entity);

    void deleteByAppKey(String appKey);

    List<ApplicationKnowledgeBindingEntity> selectByAppKey(String appKey);
}
