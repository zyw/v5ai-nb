package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

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
}
