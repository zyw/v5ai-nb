package xin.v5ai.nb.infrastructure.rag;

import java.util.List;

public interface KnowledgeBaseGateway {
    void insertKnowledgeBase(KnowledgeBaseEntity entity);

    KnowledgeBaseEntity selectKnowledgeBaseById(Long id);

    List<KnowledgeBaseEntity> selectAll();

    /** 下拉选项投影：仅 id/name。 */
    List<KnowledgeBaseEntity> selectOptions();

    void disable(Long id);
}
