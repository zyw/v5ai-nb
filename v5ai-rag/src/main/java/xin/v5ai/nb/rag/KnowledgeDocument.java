package xin.v5ai.nb.rag;

public record KnowledgeDocument(
        Long id,
        Long knowledgeBaseId,
        String title,
        DocumentFileType fileType,
        String source,
        DocumentStatus status,
        String errorMessage
) {
    public KnowledgeDocument withStatus(DocumentStatus status) {
        return new KnowledgeDocument(id, knowledgeBaseId, title, fileType, source, status, errorMessage);
    }

    public KnowledgeDocument withError(String errorMessage) {
        return new KnowledgeDocument(id, knowledgeBaseId, title, fileType, source, status, errorMessage);
    }
}
