package xin.v5ai.nb.runtime.core.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.runtime.core.domain.RuntimeRunEntity;
import xin.v5ai.nb.runtime.core.gateway.RuntimeRunGateway;

@Mapper
public interface RuntimeRunMapper extends BaseMapper<RuntimeRunEntity>, RuntimeRunGateway {
    @Override
    default void insertRun(RuntimeRunEntity entity) {
        insert(entity);
    }

    @Override
    default void markCompleted(String runId) {
        var entity = new RuntimeRunEntity();
        entity.setStatus("COMPLETED");
        update(entity, new LambdaUpdateWrapper<RuntimeRunEntity>().eq(RuntimeRunEntity::getId, runId));
    }

    @Override
    default void markFailed(String runId, String errorMessage) {
        var entity = new RuntimeRunEntity();
        entity.setStatus("FAILED");
        entity.setErrorMessage(errorMessage);
        update(entity, new LambdaUpdateWrapper<RuntimeRunEntity>().eq(RuntimeRunEntity::getId, runId));
    }
}
