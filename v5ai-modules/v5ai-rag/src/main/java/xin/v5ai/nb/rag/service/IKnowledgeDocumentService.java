package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.List;

public interface IKnowledgeDocumentService {
    /**
     * 持久化文档，并返回填充了生成主键（id）的文档对象。
     */
    KnowledgeDocument save(KnowledgeDocument document);

    /**
     * 按 ID 查询文档。
     */
    KnowledgeDocument findById(Long id);

    /**
     * 查询某知识库下的全部文档。
     */
    List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 按 ID 删除文档。
     */
    void delete(Long id);
}
