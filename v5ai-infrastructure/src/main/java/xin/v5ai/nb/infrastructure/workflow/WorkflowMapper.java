package xin.v5ai.nb.infrastructure.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowEntity>, WorkflowGateway {
    @Override
    default void insertWorkflow(WorkflowEntity entity) {
        insert(entity);
    }

    @Override
    default void updateWorkflow(WorkflowEntity entity) {
        updateById(entity);
    }

    @Override
    default WorkflowEntity selectWorkflowById(Long id) {
        return selectById(id);
    }

    @Override
    default WorkflowEntity selectWorkflowByKey(String workflowKey) {
        return selectOne(new LambdaQueryWrapper<WorkflowEntity>().eq(WorkflowEntity::getWorkflowKey, workflowKey));
    }

    @Override
    default List<WorkflowEntity> selectAllWorkflows() {
        return selectList(new LambdaQueryWrapper<WorkflowEntity>().orderByAsc(WorkflowEntity::getId));
    }

    @Override
    default void disableWorkflow(Long id) {
        var entity = new WorkflowEntity();
        entity.setStatus("DISABLED");
        update(entity, new LambdaUpdateWrapper<WorkflowEntity>().eq(WorkflowEntity::getId, id));
    }
}
