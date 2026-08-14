package xin.v5ai.nb.infrastructure.rag;

import java.util.List;

public interface KnowledgeBaseGateway {
    void insertKnowledgeBase(KnowledgeBaseEntity entity);

    KnowledgeBaseEntity selectKnowledgeBaseById(Long id);

    List<KnowledgeBaseEntity> selectAll();

    void disable(Long id);
}
