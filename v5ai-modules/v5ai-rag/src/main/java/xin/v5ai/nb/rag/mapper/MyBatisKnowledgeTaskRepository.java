package xin.v5ai.nb.rag.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.rag.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.domain.KnowledgeTask;

import java.util.List;

/**
 * {@link KnowledgeTaskRepository} 的 MyBatis-Plus 实现（Worker 索引任务消费）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
public class MyBatisKnowledgeTaskRepository implements KnowledgeTaskRepository {

    private final KnowledgeTaskMapper taskMapper;

    public MyBatisKnowledgeTaskRepository(KnowledgeTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public KnowledgeTask save(KnowledgeTask task) {
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
        return task;
    }

    @Override
    public KnowledgeTask findById(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public List<KnowledgeTask> findByDocumentId(Long documentId) {
        return taskMapper.selectList(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getDocumentId, documentId));
    }

    @Override
    public List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId) {
        return taskMapper.selectList(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeTask::getId));
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        taskMapper.delete(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getDocumentId, documentId));
    }

    @Override
    public List<KnowledgeTask> findProcessable(int limit) {
        return taskMapper.selectProcessable(limit);
    }
}
