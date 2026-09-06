package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.rag.domain.KnowledgeTask;

import java.util.List;

/**
 * 索引任务持久化端口（Worker 消费，由模块内 MyBatis 实现提供）。
 */
public interface IKnowledgeTaskService {
    /**
     * 保存/更新索引任务。
     *
     * @param task 索引任务
     * @return 保存后的任务（含主键）
     */
    KnowledgeTask save(KnowledgeTask task);

    /**
     * 按 ID 查询任务。
     */
    KnowledgeTask findById(Long id);

    /**
     * 查询某文档的全部任务。
     */
    List<KnowledgeTask> findByDocumentId(Long documentId);

    /**
     * 查询某知识库的全部任务。
     */
    List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 删除某文档的全部任务（文档删除时清理）。
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 查询可处理的任务：PENDING 状态的任务，外加仍有剩余尝试次数的 FAILED 任务。
     * 按创建时间从旧到新排序，最多返回 {@code limit} 条。
     *
     * @param limit 返回上限
     * @return 可处理的任务列表
     */
    List<KnowledgeTask> findProcessable(int limit);

    /**
     * 回收卡死的 PROCESSING 任务（进程崩溃/重启遗留），避免任务永久不被调度。
     *
     * @param staleMinutes 超过该分钟数未推进即视为卡死
     * @return 被回收的任务数
     */
    int recoverStaleProcessing(int staleMinutes);
}
