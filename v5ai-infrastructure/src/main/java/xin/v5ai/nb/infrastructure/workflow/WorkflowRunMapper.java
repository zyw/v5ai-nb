package xin.v5ai.nb.infrastructure.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunEntity>, WorkflowRunGateway {
    @Override
    default void insertRun(WorkflowRunEntity entity) {
        insert(entity);
    }

    @Override
    default void markCompleted(String runId, String outputs, Instant finishedAt) {
        var entity = new WorkflowRunEntity();
        entity.setStatus("SUCCEEDED");
        entity.setOutputs(outputs);
        entity.setFinishedAt(finishedAt);
        update(entity, new LambdaUpdateWrapper<WorkflowRunEntity>().eq(WorkflowRunEntity::getRunId, runId));
    }

    @Override
    default void markFailed(String runId, String error, Instant finishedAt) {
        var entity = new WorkflowRunEntity();
        entity.setStatus("FAILED");
        entity.setError(error);
        entity.setFinishedAt(finishedAt);
        update(entity, new LambdaUpdateWrapper<WorkflowRunEntity>().eq(WorkflowRunEntity::getRunId, runId));
    }

    @Override
    default WorkflowRunEntity selectRun(String runId) {
        return selectById(runId);
    }

    @Override
    default List<WorkflowRunEntity> selectRunsByKey(String workflowKey) {
        return selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
                .eq(WorkflowRunEntity::getWorkflowKey, workflowKey)
                .orderByDesc(WorkflowRunEntity::getCreatedAt));
    }
}
