package xin.v5ai.nb.infrastructure.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunEntity>, WorkflowNodeRunGateway {
    @Override
    default void insertNodeRun(WorkflowNodeRunEntity entity) {
        insert(entity);
    }

    @Override
    default List<WorkflowNodeRunEntity> selectNodeRunsByRun(String runId) {
        return selectList(new LambdaQueryWrapper<WorkflowNodeRunEntity>()
                .eq(WorkflowNodeRunEntity::getRunId, runId)
                .orderByAsc(WorkflowNodeRunEntity::getId));
    }
}
