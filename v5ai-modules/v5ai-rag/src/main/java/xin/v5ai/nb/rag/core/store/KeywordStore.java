package xin.v5ai.nb.rag.core.store;

import java.util.List;

/**
 * 关键词检索端口：负责 chunk 文本的写入与关键词召回（搜索引擎后端，如 ES match / PG jieba 分词 + Okapi BM25）。
 * 与 {@link VectorStore} 分离，以支持异构后端：Milvus 仅实现向量检索，ES/PG 可同时实现两者。
 */
public interface KeywordStore {

    /**
     * 写入待关键词检索的切片（复用 {@link VectorStore.VectorChunk}，embedding 字段可忽略）。
     */
    void store(List<VectorStore.VectorChunk> chunks);

    /**
     * 删除某文档的全部分词切片。
     *
     * @param knowledgeBaseId 所属知识库 ID
     * @param documentId      文档 ID
     */
    void deleteByDocumentId(Long knowledgeBaseId, Long documentId);

    /**
     * 关键词检索：在指定知识库内按关键词召回 topK 条结果。
     *
     * @param knowledgeBaseIds 限定的知识库 ID 集合
     * @param keyword          关键词
     * @param topK             返回条数
     * @return 命中结果列表（按得分降序）
     */
    List<VectorStore.RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK);
}
