package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuntimeRunEventMapper extends BaseMapper<RuntimeRunEventEntity>, RuntimeRunEventGateway {
    @Override
    default void insertRunEvent(RuntimeRunEventEntity entity) {
        insert(entity);
    }
}
