package xin.v5ai.nb.infrastructure.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationVersionMapper extends BaseMapper<ApplicationVersionEntity>, ApplicationVersionGateway {
    @Override
    default void insertVersion(ApplicationVersionEntity entity) {
        insert(entity);
    }

    @Override
    default long countVersions(String appKey) {
        return selectCount(new LambdaQueryWrapper<ApplicationVersionEntity>()
                .eq(ApplicationVersionEntity::getAppKey, appKey));
    }
}
