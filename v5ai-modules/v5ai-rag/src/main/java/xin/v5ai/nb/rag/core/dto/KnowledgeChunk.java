package xin.v5ai.nb.rag.core.dto;

public record KnowledgeChunk(
        Long id,
        Long knowledgeBaseId,
        Long documentId,
        int chunkIndex,
        String content,
        String metadata,
        Integer paragraphIndex,
        Integer tokenCount,
        String vectorId,
        String contentHash,
        String sourceType
) {
    public KnowledgeChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String metadata) {
        this(null, knowledgeBaseId, documentId, chunkIndex, content, metadata, null, null, null, null, "TEXT");
    }
}
