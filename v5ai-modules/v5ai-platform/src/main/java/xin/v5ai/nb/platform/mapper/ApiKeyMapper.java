package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.ApiKey;

import java.util.List;

/**
 * Agent API Key（v5ai_api_key）Mapper：原 ApiKeyGateway 的默认方法直接并入。
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
    default void upsert(ApiKey entity) {
        var existing = selectByAgentKey(entity.getAgentKey());
        if (existing == null) {
            insert(entity);
        } else {
            entity.setId(existing.getId());
            updateById(entity);
        }
    }

    default ApiKey selectByAgentKey(String agentKey) {
        return selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getAgentKey, agentKey));
    }

    default List<ApiKey> selectAll() {
        return selectList(null);
    }

    default void deleteByAgentKey(String agentKey) {
        delete(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getAgentKey, agentKey));
    }
}
