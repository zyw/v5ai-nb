package xin.v5ai.nb.infrastructure.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.VectorStore;

import java.util.ArrayList;
import java.util.List;

/**
 * pgvector-backed vector store using {@code JdbcTemplate}. Chunks are stored with a
 * {@code vector(1536)} embedding and searched with cosine similarity ({@code <=>}).
 * Keyword search falls back to {@code ILIKE} over the chunk content.
 */
@Component
public class PgVectorStore implements VectorStore {
    private final JdbcTemplate jdbcTemplate;

    public PgVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void store(List<VectorChunk> chunks) {
        for (var chunk : chunks) {
            jdbcTemplate.update("""
                            INSERT INTO v5ai_knowledge_chunk
                                (knowledge_base_id, document_id, chunk_index, content, embedding, metadata)
                            VALUES (?, ?, ?, ?, ?::vector, ?::jsonb)
                            """,
                    chunk.knowledgeBaseId(),
                    chunk.documentId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    toVectorLiteral(chunk.embedding()),
                    chunk.metadata() == null ? "{}" : chunk.metadata());
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update("DELETE FROM v5ai_knowledge_chunk WHERE document_id = ?", documentId);
    }

    @Override
    public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        var placeholders = String.join(",", knowledgeBaseIds.stream().map(id -> "?").toList());
        var sql = """
                SELECT knowledge_base_id, document_id, chunk_index, content, metadata,
                       1 - (embedding <=> ?::vector) AS score
                FROM v5ai_knowledge_chunk
                WHERE knowledge_base_id IN (%s)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders);
        var params = new ArrayList<>();
        params.add(toVectorLiteral(queryVector));
        params.addAll(knowledgeBaseIds);
        params.add(toVectorLiteral(queryVector));
        params.add(topK);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievalHit(
                rs.getLong("knowledge_base_id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("score")), params.toArray());
    }

    @Override
    public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        var placeholders = String.join(",", knowledgeBaseIds.stream().map(id -> "?").toList());
        var sql = """
                SELECT knowledge_base_id, document_id, chunk_index, content, metadata, 1.0 AS score
                FROM v5ai_knowledge_chunk
                WHERE knowledge_base_id IN (%s) AND content ILIKE ?
                ORDER BY id
                LIMIT ?
                """.formatted(placeholders);
        var params = new ArrayList<>();
        params.addAll(knowledgeBaseIds);
        params.add("%" + keyword + "%");
        params.add(topK);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievalHit(
                rs.getLong("knowledge_base_id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("score")), params.toArray());
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
