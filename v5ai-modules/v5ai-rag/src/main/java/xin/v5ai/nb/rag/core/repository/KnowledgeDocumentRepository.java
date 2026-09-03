//package xin.v5ai.nb.rag.core.repository;
//
//import xin.v5ai.nb.rag.domain.KnowledgeDocument;
//
//import java.util.List;
//
///**
// * 文档持久化端口（Worker 与检索链路消费，由模块内 MyBatis 实现提供）。
// */
//public interface KnowledgeDocumentRepository {
//    /**
//     * 持久化文档，并返回填充了生成主键（id）的文档对象。
//     */
//    KnowledgeDocument save(KnowledgeDocument document);
//
//    /**
//     * 按 ID 查询文档。
//     */
//    KnowledgeDocument findById(Long id);
//
//    /**
//     * 查询某知识库下的全部文档。
//     */
//    List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId);
//
//    /**
//     * 按 ID 删除文档。
//     */
//    void delete(Long id);
//}
