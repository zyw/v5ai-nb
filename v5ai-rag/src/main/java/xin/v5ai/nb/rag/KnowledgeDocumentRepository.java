package xin.v5ai.nb.rag;

import java.util.List;

public interface KnowledgeDocumentRepository {
    /**
     * Persists the document and returns it with the generated id populated.
     */
    KnowledgeDocument save(KnowledgeDocument document);

    KnowledgeDocument findById(Long id);

    List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId);

    void delete(Long id);
}
