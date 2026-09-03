package xin.v5ai.nb.rag.core.store;

import java.util.List;

/**
 * 向量存储端口：负责向量持久化与相似度检索。
 * 一个切片与其 Embedding 向量、检索元数据一同存储（默认实现为 pgvector）。
 */
public interface VectorStore {

    /**
     * 待写入的向量切片：文档某一片段及其向量与元数据。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param documentId      所属文档 ID
     * @param chunkIndex      片内序号
     * @param content         切片原文
     * @param embedding       该切片的向量
     * @param metadata        检索元数据（JSON，如标题/页码/文件名）
     * @param tokenCount      分片 token 数量（空白切分估算，可为 null）
     * @param contentHash     切片内容 SHA-256（用于向量去重，可为 null）
     * @param sourceType      chunk 来源类型（TEXT=文本 / IMAGE=图片）
     */
    record VectorChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content,
                       List<Float> embedding, String metadata, Integer tokenCount, String contentHash,
                       String sourceType) {
    }

    /**
     * 检索命中结果：除切片信息外附带相似度得分。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param documentId      所属文档 ID
     * @param chunkIndex      片内序号
     * @param content         切片原文
     * @param metadata        检索元数据
     * @param score           相似度得分（越高越相关）
     */
    record RetrievalHit(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String metadata,
                        double score) {
    }

    /**
     * 批量写入向量切片。
     *
     * @param chunks 要存储的切片集合
     */
    void store(List<VectorChunk> chunks);

    /**
     * 删除某文档的全部向量切片（文档删除/重新索引时调用）。
     * 按知识库定位后端索引/集合（ES/Milvus 每个知识库一个索引/集合）。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param documentId      文档 ID
     */
    void deleteByDocumentId(Long knowledgeBaseId, Long documentId);

    /**
     * 向量相似度检索：用查询向量在指定知识库内召回 topK 条结果。
     *
     * @param knowledgeBaseIds 限定的知识库 ID 集合
     * @param queryVector      查询向量
     * @param topK             返回条数
     * @return 命中结果列表（按得分降序）
     */
    List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK);
}
