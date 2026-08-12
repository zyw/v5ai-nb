package xin.v5ai.nb.infrastructure.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity>, ModelConfigGateway {
    @Override
    default void insertModel(ModelConfigEntity entity) {
        insert(entity);
    }
}
