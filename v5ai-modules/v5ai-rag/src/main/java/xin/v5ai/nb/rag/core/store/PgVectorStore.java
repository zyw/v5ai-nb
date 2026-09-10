package xin.v5ai.nb.rag.core.store;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * pgvector-backed vector store：每知识库一张独立向量表 {@code v5ai_vec_{knowledgeBaseId}}，
 * 维度在建表时按该知识库冻结（向量承载与 chunk 业务表解耦，业务行只保留 {@code vector_id}）。
 * <p>
 * 向量检索按余弦相似度（{@code <=>}）召回并返回 {@code (vectorId, score)}，内容由调用方回业务表补齐；
 * 关键词检索走 {@link PgBm25KeywordSearch}（jieba 分词 + Okapi BM25，索引列为业务表
 * {@code keyword_tokens}，由 Worker/Admin 维护），查询文本无法分词时回退 {@code content ILIKE} 粗检。
 */
@Slf4j
public class PgVectorStore implements VectorStore, KeywordStore {

    private static final String TABLE_PREFIX = "v5ai_vec_";

    private final JdbcTemplate jdbcTemplate;
    private final IKnowledgeChunkService chunkService;
    private final PgBm25KeywordSearch bm25KeywordSearch;
    private final DataSource dataSource;

    public PgVectorStore(DataSource dataSource, IKnowledgeChunkService chunkService,
                         PgBm25KeywordSearch bm25KeywordSearch) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.chunkService = chunkService;
        this.bm25KeywordSearch = bm25KeywordSearch;
    }

    public void close() {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IllegalStateException("关闭 PostgreSQL 向量存储连接失败", e);
            }
        }
    }

    // ---------- VectorStore ----------

    @Override
    public void store(List<VectorChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        VectorChunk vc = chunks.getFirst();
        Long knowledgeBaseId = vc.knowledgeBaseId();
        int dimension = vc.embedding() == null ? 0 : vc.embedding().size();
        if (dimension == 0) {
            throw new IllegalArgumentException("向量维度不能为空: kb=" + knowledgeBaseId);
        }
        ensureCollection(knowledgeBaseId, dimension);
        for (VectorChunk chunk : chunks) {
            if (StrUtil.isBlank(chunk.vectorId())) {
                throw new IllegalArgumentException("切片向量标识(vector_id)不能为空: doc=" + chunk.documentId());
            }
            jdbcTemplate.update("""
                            INSERT INTO %s (vector_id, document_id, chunk_index, embedding)
                            VALUES (?, ?, ?, ?::vector)
                            ON CONFLICT (vector_id) DO UPDATE SET embedding = EXCLUDED.embedding
                            """.formatted(tableName(knowledgeBaseId)),
                    chunk.vectorId(), chunk.documentId(), chunk.chunkIndex(), toVectorLiteral(chunk.embedding()));
        }
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        if (!tableExists(knowledgeBaseId)) {
            // 该知识库从未索引过（无向量表）：无向量可删，等同成功
            return;
        }
        jdbcTemplate.update("DELETE FROM %s WHERE document_id = ?".formatted(tableName(knowledgeBaseId)),
                documentId);
    }

    @Override
    public void deleteByVectorId(Long knowledgeBaseId, String vectorId) {
        if (!tableExists(knowledgeBaseId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM %s WHERE vector_id = ?".formatted(tableName(knowledgeBaseId)),
                vectorId);
    }

    @Override
    public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
        if (CollUtil.isEmpty(knowledgeBaseIds) || queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        var hits = new ArrayList<RetrievalHit>();
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            try {
                List<RetrievalHit> perBase = jdbcTemplate.query("""
                                SELECT vector_id, document_id, chunk_index,
                                       1 - (embedding <=> ?::vector) AS score
                                FROM %s
                                ORDER BY embedding <=> ?::vector
                                LIMIT ?
                                """.formatted(tableName(knowledgeBaseId)),
                        (rs, rowNum) -> new RetrievalHit(
                                rs.getString("vector_id"),
                                knowledgeBaseId,
                                rs.getLong("document_id"),
                                rs.getInt("chunk_index"),
                                null,
                                null,
                                rs.getDouble("score")),
                        toVectorLiteral(queryVector), toVectorLiteral(queryVector), topK);
                hits.addAll(perBase);
            } catch (Exception exception) {
                // 集合/表不存在或维度不匹配：该知识库降级为空，继续其余知识库
                log.warn("pgvector search skipped for kb {}: {}", knowledgeBaseId, exception.getMessage());
            }
        }
        hits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    /**
     * ivfflat 索引的维度硬上限（pgvector 对 IVF 类索引的固有限制）。
     * 高于此维度应使用 HNSW 索引（pgvector 0.5+ 列支持至 16000 维，hnsw 支持与否取决于部署版本）。
     */
    private static final int IVFFLAT_MAX_DIMENSIONS = 2000;

    /**
     * 幂等创建某知识库的向量表与相似度索引（维度在建表时固定；维度变更需重建集合）。
     * <p>
     * 索引策略：≤{@link #IVFFLAT_MAX_DIMENSIONS} 建 ivfflat；超过时尝试 HNSW。
     * 索引创建失败（如部署的 pgvector hnsw 上限低于当前维度）时**降级为无索引精确检索**并告警，
     * 不阻断写入——`ORDER BY embedding <=> ?` 在无索引下走全表扫描，结果仍正确（大数据量需升级
     * pgvector 或改用支持高维的向量库后再建索引）。
     */
    public void ensureCollection(Long knowledgeBaseId, int dimension) {
        if (knowledgeBaseId == null || dimension <= 0) {
            throw new IllegalArgumentException("知识库与向量维度必须有效: kb=" + knowledgeBaseId + ", dim=" + dimension);
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    vector_id   varchar(64) PRIMARY KEY,
                    document_id bigint      NOT NULL,
                    chunk_index integer     NOT NULL,
                    embedding   vector(%d)  NOT NULL
                )
                """.formatted(tableName(knowledgeBaseId), dimension));
        String indexDdl;
        if (dimension > IVFFLAT_MAX_DIMENSIONS) {
            indexDdl = "CREATE INDEX IF NOT EXISTS %s ON %s USING hnsw (embedding vector_cosine_ops)"
                    .formatted(indexName(knowledgeBaseId), tableName(knowledgeBaseId));
        } else {
            indexDdl = "CREATE INDEX IF NOT EXISTS %s ON %s USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
                    .formatted(indexName(knowledgeBaseId), tableName(knowledgeBaseId));
        }
        try {
            jdbcTemplate.execute(indexDdl);
        } catch (Exception exception) {
            log.warn("kb {} 向量索引创建失败（{}），降级为无索引精确检索；如需 ANN 加速请升级 pgvector "
                    + "或改用支持高维的向量库: {}", knowledgeBaseId, indexDdl, exception.getMessage());
        }
    }

    // ---------- KeywordStore（PG：jieba 分词 + BM25，词项为空时回退 ILIKE 粗检） ----------

    @Override
    public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
        if (CollUtil.isEmpty(knowledgeBaseIds) || StrUtil.isBlank(keyword)) {
            return List.of();
        }
        // 查询文本分词后无有效词项（全停用词/标点）时保持原 ILIKE 行为，不返回空错
        return bm25KeywordSearch.search(knowledgeBaseIds, keyword, topK)
                .orElseGet(() -> ilikeKeywordSearch(knowledgeBaseIds, keyword, topK));
    }

    /**
     * 关键词粗检回退：业务表 {@code content ILIKE %}（命中恒 1.0、无相关度排序）。
     * 仅在查询文本无法分词时使用（BM25 需要词项才能取候选）。
     */
    private List<RetrievalHit> ilikeKeywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
        var result = chunkService.findChunkByIdsAndLinkKeyword(knowledgeBaseIds, "%" + keyword.trim() + "%", topK);
        if (CollUtil.isEmpty(result)) {
            return List.of();
        }
        return result.stream().map(chunk -> new RetrievalHit(
                chunk.getVectorId(),
                chunk.getKnowledgeBaseId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                StrUtil.toString(chunk.getMetadata()),
                chunk.getScore() == null ? 1.0 : chunk.getScore())).toList();
    }

    private static String tableName(Long knowledgeBaseId) {
        return TABLE_PREFIX + knowledgeBaseId;
    }

    private boolean tableExists(Long knowledgeBaseId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
                    Integer.class, tableName(knowledgeBaseId));
            return count != null && count == 1;
        } catch (Exception exception) {
            return false;
        }
    }

    private static String indexName(Long knowledgeBaseId) {
        return "idx_" + TABLE_PREFIX + knowledgeBaseId + "_embedding";
    }

    private static String toVectorLiteral(List<Float> embedding) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding.get(i));
        }
        return sb.append(']').toString();
    }
}
