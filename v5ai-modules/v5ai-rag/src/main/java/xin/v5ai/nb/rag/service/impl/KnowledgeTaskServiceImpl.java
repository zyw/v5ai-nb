package xin.v5ai.nb.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.rag.domain.KnowledgeTask;
import xin.v5ai.nb.rag.mapper.KnowledgeTaskMapper;
import xin.v5ai.nb.rag.service.IKnowledgeTaskService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeTaskServiceImpl implements IKnowledgeTaskService {

    private final KnowledgeTaskMapper taskMapper;

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

    @Override
    public int recoverStaleProcessing(int staleMinutes) {
        return taskMapper.recoverStaleProcessing(staleMinutes);
    }
}
