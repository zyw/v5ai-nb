package xin.v5ai.nb.rag.core.store;

import java.util.List;

/**
 * 向量存储端口：负责向量的持久化与相似度检索。
 * <p>
 * 与知识库切片的业务行解耦：切片内容/元数据落 {@code v5ai_knowledge_chunk} 业务表，
 * 向量落在存储实例内按知识库隔离的集合（pgvector=每知识库一张向量表，Milvus=collection，
 * Elasticsearch=index），集合内以业务生成的 {@code vectorId}(UUID) 为主键关联业务行。
 * 检索先在本端口召回 {@code (vectorId, score)}，再由调用方按 vectorId 回业务表取内容。
 */
public interface VectorStore {

    /**
     * 待写入的向量切片：业务切片元数据 + 向量。
     *
     * @param vectorId       业务生成的稳定 UUID，集合主键，与 {@code v5ai_knowledge_chunk.vector_id} 一致
     * @param knowledgeBaseId 所属知识库 ID（决定集合归属）
     * @param documentId     所属文档 ID
     * @param chunkIndex     片内序号
     * @param content        切片原文（部分后端如 ES 需保留原文用于关键词检索）
     * @param embedding      该切片的向量
     * @param metadata       检索元数据（JSON，如标题/页码/文件名）
     * @param tokenCount     分片 token 数量（空白切分估算，可为 null）
     * @param contentHash    切片内容 SHA-256（用于向量去重，可为 null）
     * @param sourceType     chunk 来源类型（TEXT=文本 / IMAGE=图片）
     */
    record VectorChunk(String vectorId, Long knowledgeBaseId, Long documentId, int chunkIndex, String content,
                       List<Float> embedding, String metadata, Integer tokenCount, String contentHash,
                       String sourceType) {
    }

    /**
     * 检索命中结果：携带集合主键 {@code vectorId} 与定位信息、相似度得分。
     * content/metadata 由调用方按 vectorId 回业务表取回，字段可空。
     *
     * @param vectorId       业务生成的向量标识（集合主键）
     * @param knowledgeBaseId 所属知识库 ID
     * @param documentId     所属文档 ID
     * @param chunkIndex     片内序号
     * @param content        切片原文（可为 null，由调用方回业务表补齐）
     * @param metadata       检索元数据（可为 null）
     * @param score          相似度得分（越高越相关）
     */
    record RetrievalHit(String vectorId, Long knowledgeBaseId, Long documentId, int chunkIndex, String content,
                        String metadata, double score) {
    }

    /**
     * 批量写入向量切片；首次写入某知识库时幂等创建其集合（惰性，维度取首个向量长度）。
     *
     * @param chunks 要存储的切片集合（须携带非空 {@code vectorId}）
     */
    void store(List<VectorChunk> chunks);

    /**
     * 删除某文档的全部向量（文档删除/重新索引时调用）。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param documentId      文档 ID
     */
    void deleteByDocumentId(Long knowledgeBaseId, Long documentId);

    /**
     * 删除单个切片向量（手工切片编辑重嵌/删除时调用）。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param vectorId        切片向量标识（集合主键）
     */
    void deleteByVectorId(Long knowledgeBaseId, String vectorId);

    /**
     * 向量相似度检索：用查询向量在指定知识库内召回 topK 条结果。
     *
     * @param knowledgeBaseIds 限定的知识库 ID 集合
     * @param queryVector      查询向量
     * @param topK             返回条数
     * @return 命中结果列表（按得分降序，content/metadata 可为空）
     */
    List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK);
}
