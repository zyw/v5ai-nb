package xin.v5ai.nb.infrastructure.rag;

import java.util.List;

public interface KnowledgeTaskGateway {
    void insertTask(KnowledgeTaskEntity entity);

    void update(KnowledgeTaskEntity entity);

    KnowledgeTaskEntity selectTaskById(Long id);

    List<KnowledgeTaskEntity> selectByDocumentId(Long documentId);

    List<KnowledgeTaskEntity> selectByKnowledgeBaseId(Long knowledgeBaseId);

    void deleteByDocumentId(Long documentId);

    List<KnowledgeTaskEntity> selectProcessable(int limit);
}
