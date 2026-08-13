package xin.v5ai.nb.infrastructure.runtime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuntimeMessageMapper extends BaseMapper<RuntimeMessageEntity>, RuntimeMessageGateway {
    @Override
    default void insertMessage(RuntimeMessageEntity entity) {
        insert(entity);
    }
}
