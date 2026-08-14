package xin.v5ai.nb.infrastructure.apiKey;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity>, ApiKeyGateway {
    @Override
    default void upsert(ApiKeyEntity entity) {
        var existing = selectByAppKey(entity.getAppKey());
        if (existing == null) {
            insert(entity);
        } else {
            entity.setId(existing.getId());
            updateById(entity);
        }
    }

    @Override
    default ApiKeyEntity selectByAppKey(String appKey) {
        return selectOne(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getAppKey, appKey));
    }

    @Override
    default List<ApiKeyEntity> selectAll() {
        return selectList(null);
    }

    @Override
    default void deleteByAppKey(String appKey) {
        delete(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getAppKey, appKey));
    }
}
