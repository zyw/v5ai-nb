package xin.v5ai.nb.rag;

public record KnowledgeTask(
        Long id,
        Long knowledgeBaseId,
        Long documentId,
        TaskStatus status,
        int attemptCount,
        int maxAttempts,
        String errorMessage
) {
    public KnowledgeTask withStatus(TaskStatus status) {
        return new KnowledgeTask(id, knowledgeBaseId, documentId, status, attemptCount, maxAttempts, errorMessage);
    }

    public KnowledgeTask withAttempt() {
        return new KnowledgeTask(id, knowledgeBaseId, documentId, status, attemptCount + 1, maxAttempts, errorMessage);
    }

    public KnowledgeTask withError(String errorMessage) {
        return new KnowledgeTask(id, knowledgeBaseId, documentId, status, attemptCount, maxAttempts, errorMessage);
    }
}
