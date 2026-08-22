package xin.v5ai.nb.rag;

import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.List;

/**
 * 文档持久化端口（Worker 与检索链路消费，由模块内 MyBatis 实现提供）。
 */
public interface KnowledgeDocumentRepository {
    /**
     * Persists the document and returns it with the generated id populated.
     */
    KnowledgeDocument save(KnowledgeDocument document);

    KnowledgeDocument findById(Long id);

    List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId);

    void delete(Long id);
}
