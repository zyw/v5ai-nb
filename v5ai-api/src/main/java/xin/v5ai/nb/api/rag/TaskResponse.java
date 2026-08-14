package xin.v5ai.nb.api.rag;

import xin.v5ai.nb.rag.KnowledgeTask;
import xin.v5ai.nb.rag.TaskStatus;

public record TaskResponse(
        Long id,
        Long knowledgeBaseId,
        Long documentId,
        TaskStatus status,
        int attemptCount,
        int maxAttempts,
        String errorMessage
) {
    static TaskResponse from(KnowledgeTask task) {
        return new TaskResponse(task.id(), task.knowledgeBaseId(), task.documentId(), task.status(),
                task.attemptCount(), task.maxAttempts(), task.errorMessage());
    }
}
