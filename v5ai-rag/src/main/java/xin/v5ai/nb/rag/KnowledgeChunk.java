package xin.v5ai.nb.rag;

public record KnowledgeChunk(
        Long id,
        Long knowledgeBaseId,
        Long documentId,
        int chunkIndex,
        String content,
        String metadata
) {
    public KnowledgeChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String metadata) {
        this(null, knowledgeBaseId, documentId, chunkIndex, content, metadata);
    }
}
