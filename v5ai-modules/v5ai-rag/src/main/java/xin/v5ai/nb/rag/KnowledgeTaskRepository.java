package xin.v5ai.nb.rag;

import xin.v5ai.nb.rag.domain.KnowledgeTask;

import java.util.List;

/**
 * 索引任务持久化端口（Worker 消费，由模块内 MyBatis 实现提供）。
 */
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
