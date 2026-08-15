package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApplicationMcpBindingMapper extends BaseMapper<ApplicationMcpBindingEntity>,
        ApplicationMcpBindingGateway {
    @Override
    default void insertBinding(ApplicationMcpBindingEntity entity) {
        insert(entity);
    }

    @Override
    default void deleteByAppKey(String appKey) {
        delete(new LambdaQueryWrapper<ApplicationMcpBindingEntity>()
                .eq(ApplicationMcpBindingEntity::getAppKey, appKey));
    }

    @Override
    default List<ApplicationMcpBindingEntity> selectByAppKey(String appKey) {
        return selectList(new LambdaQueryWrapper<ApplicationMcpBindingEntity>()
                .eq(ApplicationMcpBindingEntity::getAppKey, appKey));
    }
}
