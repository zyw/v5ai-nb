package xin.v5ai.nb.rag;

import java.util.List;

public interface KnowledgeTaskRepository {
    KnowledgeTask save(KnowledgeTask task);

    KnowledgeTask findById(Long id);

    List<KnowledgeTask> findByDocumentId(Long documentId);

    List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId);

    void deleteByDocumentId(Long documentId);

    /**
     * Tasks ready for processing: PENDING tasks, plus FAILED tasks that still have
     * attempts left. Ordered oldest first, limited to {@code limit}.
     */
    List<KnowledgeTask> findProcessable(int limit);
}
