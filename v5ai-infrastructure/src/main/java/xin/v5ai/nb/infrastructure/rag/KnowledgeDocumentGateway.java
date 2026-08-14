package xin.v5ai.nb.infrastructure.rag;

import java.util.List;

public interface KnowledgeDocumentGateway {
    void insertDocument(KnowledgeDocumentEntity entity);

    void update(KnowledgeDocumentEntity entity);

    void updateContent(Long documentId, byte[] content);

    KnowledgeDocumentEntity selectKnowledgeDocumentById(Long id);

    List<KnowledgeDocumentEntity> selectByKnowledgeBaseId(Long knowledgeBaseId);

    void deleteById(Long id);
}
