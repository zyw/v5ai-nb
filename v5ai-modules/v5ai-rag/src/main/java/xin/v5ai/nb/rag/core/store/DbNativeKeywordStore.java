package xin.v5ai.nb.rag.core.store;

import lombok.RequiredArgsConstructor;
import xin.v5ai.nb.rag.core.store.VectorStore.RetrievalHit;

import java.util.List;

/**
 * 业务库原生关键词检索后端（存储实例类型 {@code 4 = DB_FULLTEXT}，历史名 {@code PG_FULLTEXT}）。
 * <p>
 * 与 {@link PgVectorStore} / {@link ElasticsearchVectorStore} 不同，它**不连任何外部服务**：
 * 分词就存在业务库的 {@code v5ai_knowledge_chunk.keyword_tokens} 上，检索走
 * {@link PgBm25KeywordSearch}（方言分支在 {@code KnowledgeChunkMapper.xml}，PG 用 {@code text[]} + GIN、
 * MySQL 用 {@code JSON} + 多值函数索引，见 docs/adr/0012）。它的意义是让**没有 pgvector / ES 的部署**
 * （典型是 MySQL + Milvus 的组合）仍然有第二路关键词召回，而不是只剩向量一路。
 * <p>
 * 因为是业务行的一个「检索视图」而非独立副本，{@link #store} 与 {@link #deleteByDocumentId} 是空操作：
 * 分词列由索引 Worker 与切片增改流程随业务行一起写、一起删（见
 * {@code KnowledgeIndexingService}、{@code KnowledgeChunkAdminServiceImpl}）。
 * 它也不实现 {@link VectorStore}——把该实例配成知识库的向量库会被
 * {@link VectorStoreResolver#vectorStore} 明确拒绝。
 *
 * @author ZYW
 * @since 2026-09-23
 */
@RequiredArgsConstructor
public class DbNativeKeywordStore implements KeywordStore {

    private final PgBm25KeywordSearch bm25KeywordSearch;

    /**
     * 空操作：分词随业务切片行落库，本实例不持有独立副本。
     */
    @Override
    public void store(List<VectorStore.VectorChunk> chunks) {
        // 刻意什么都不做
    }

    /**
     * 空操作：同上，业务行删除时分词列一并消失。
     */
    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        // 刻意什么都不做
    }

    @Override
    public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
        return bm25KeywordSearch.search(knowledgeBaseIds, keyword, topK).orElse(List.of());
    }
}
