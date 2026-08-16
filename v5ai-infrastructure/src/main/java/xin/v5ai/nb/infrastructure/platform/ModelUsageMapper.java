package xin.v5ai.nb.infrastructure.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ModelUsageMapper extends BaseMapper<ModelUsageEntity> {
    default List<ModelUsageEntity> selectByAppRange(String appKey, Instant from, Instant to, int limit) {
        return selectList(new LambdaQueryWrapper<ModelUsageEntity>()
                .eq(ModelUsageEntity::getAppKey, appKey)
                .ge(ModelUsageEntity::getCreatedAt, from)
                .le(ModelUsageEntity::getCreatedAt, to)
                .orderByDesc(ModelUsageEntity::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
    }

    default List<ModelUsageEntity> selectRecent(int limit) {
        return selectList(new LambdaQueryWrapper<ModelUsageEntity>()
                .orderByDesc(ModelUsageEntity::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
    }
}
