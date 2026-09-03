package xin.v5ai.nb.rag.core.store;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.utils.ObjectUtils;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;

import java.util.ArrayList;
import java.util.List;

/**
 * pgvector-backed vector store using {@code JdbcTemplate}. Chunks are stored with a
 * {@code vector(1536)} embedding and searched with cosine similarity ({@code <=>}).
 * Keyword search falls back to {@code ILIKE} over the chunk content.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorStore implements VectorStore, KeywordStore {

//    private final JdbcTemplate jdbcTemplate;

    private final IKnowledgeChunkService chunkService;

    @Override
    public void store(List<VectorChunk> chunks) {
        List<KnowledgeChunk> knowledgeChunks = new ArrayList<>(chunks.size());
        for (var chunk : chunks) {
            var kc = new KnowledgeChunk();
            kc.setKnowledgeBaseId(chunk.knowledgeBaseId());
            kc.setDocumentId(chunk.documentId());
            kc.setChunkIndex(chunk.chunkIndex());
            kc.setContent(chunk.content());
            kc.setEmbedding(toVectorLiteral(chunk.embedding()));
            kc.setMetadata(ObjectUtils.defaultIfNull(chunk.metadata(), "{}"));
            kc.setTokenCount(chunk.tokenCount());
            kc.setContentHash(chunk.contentHash());
            kc.setSourceType(ObjectUtils.defaultIfNull(chunk.sourceType(), "TEXT"));
            knowledgeChunks.add(kc);
//            jdbcTemplate.update("""
//                            INSERT INTO v5ai_knowledge_chunk
//                                (knowledge_base_id, document_id, chunk_index, content, embedding, metadata,
//                                 token_count, content_hash, source_type)
//                            VALUES (?, ?, ?, ?, ?::vector, ?::jsonb, ?, ?, ?)
//                            """,
//                    chunk.knowledgeBaseId(),
//                    chunk.documentId(),
//                    chunk.chunkIndex(),
//                    chunk.content(),
//                    toVectorLiteral(chunk.embedding()),
//                    chunk.metadata() == null ? "{}" : chunk.metadata(),
//                    chunk.tokenCount(),
//                    chunk.contentHash(),
//                    chunk.sourceType() == null ? "TEXT" : chunk.sourceType());
        }
        chunkService.saveBatch(knowledgeChunks);
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
//        jdbcTemplate.update("DELETE FROM v5ai_knowledge_chunk WHERE knowledge_base_id = ? AND document_id = ?",
//                knowledgeBaseId, documentId);
        chunkService.deleteByDocumentId(knowledgeBaseId, documentId);
    }

    @Override
    public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            log.warn("No knowledge base ids provided for search");
            return List.of();
        }
//        var placeholders = String.join(",", knowledgeBaseIds.stream().map(id -> "?").toList());
//        var sql = """
//                SELECT knowledge_base_id, document_id, chunk_index, content, metadata,
//                       1 - (embedding <=> ?::vector) AS score
//                FROM v5ai_knowledge_chunk
//                WHERE knowledge_base_id IN (%s)
//                ORDER BY embedding <=> ?::vector
//                LIMIT ?
//                """.formatted(placeholders);
//        var params = new ArrayList<>();
//        params.add(toVectorLiteral(queryVector));
//        params.addAll(knowledgeBaseIds);
//        params.add(toVectorLiteral(queryVector));
//        params.add(topK);
//
//        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievalHit(
//                rs.getLong("knowledge_base_id"),
//                rs.getLong("document_id"),
//                rs.getInt("chunk_index"),
//                rs.getString("content"),
//                rs.getString("metadata"),
//                rs.getDouble("score")), params.toArray());
        var result = chunkService.findChunkByBaseIds(toVectorLiteral(queryVector),knowledgeBaseIds,toVectorLiteral(queryVector),topK);
        if(CollUtil.isEmpty(result)) {
            log.warn("No results found for query vector {} in knowledge base ids {}", queryVector, knowledgeBaseIds);
            return List.of();
        }
        return result.stream().map(chunk -> new RetrievalHit(
                chunk.getKnowledgeBaseId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                StrUtil.toString(chunk.getMetadata()),
                chunk.getScore())).toList(); // Changed from 1.0 to 1.0
    }

    @Override
    public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || keyword == null || keyword.isBlank()) {
            log.warn("No knowledge base ids or keyword provided for keyword search");
            return List.of();
        }
//        var placeholders = String.join(",", knowledgeBaseIds.stream().map(id -> "?").toList());
//        var sql = """
//                SELECT knowledge_base_id, document_id, chunk_index, content, metadata, 1.0 AS score
//                FROM v5ai_knowledge_chunk
//                WHERE knowledge_base_id IN (%s) AND content ILIKE ?
//                ORDER BY id
//                LIMIT ?
//                """.formatted(placeholders);
//        var params = new ArrayList<>();
//        params.addAll(knowledgeBaseIds);
//        params.add("%" + keyword + "%");
//        params.add(topK);
//        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievalHit(
//                rs.getLong("knowledge_base_id"),
//                rs.getLong("document_id"),
//                rs.getInt("chunk_index"),
//                rs.getString("content"),
//                rs.getString("metadata"),
//                rs.getDouble("score")), params.toArray());
        var result = chunkService.findChunkByIdsAndLinkKeyword(knowledgeBaseIds, "%" + keyword + "%", topK);
        if(CollUtil.isEmpty(result)) {
            log.warn("No results found for keyword {} in knowledge base ids {}", keyword, knowledgeBaseIds);
            return List.of();
        }
        return result.stream().map(chunk -> new RetrievalHit(
                chunk.getKnowledgeBaseId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                StrUtil.toString(chunk.getMetadata()),
                chunk.getScore())).toList(); // Changed from 1.0 to 1.0
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
