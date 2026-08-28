package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.V5aiApiKey;
import xin.v5ai.nb.platform.domain.vo.V5aiApiKeyVo;

import java.util.List;

/**
 * Agent API Key（v5ai_api_key）Mapper：原 ApiKeyGateway 的默认方法直接并入。
 */
@Mapper
public interface V5aiApiKeyMapper extends BaseMapperPlus<V5aiApiKey, V5aiApiKeyVo> {

    default int upsert(V5aiApiKey entity) {
        var existing = selectByAgentKey(entity.getAgentKey());
        if (existing == null) {
            return insert(entity);
        } else {
            entity.setId(existing.getId());
            return updateById(entity);
        }
    }

    default V5aiApiKey selectByAgentKey(String agentKey) {
        return selectOne(new LambdaQueryWrapper<V5aiApiKey>()
                .eq(V5aiApiKey::getAgentKey, agentKey));
    }

    default List<V5aiApiKey> selectAll() {
        return selectList(null);
    }

    default int deleteByAgentKey(String agentKey) {
        return delete(new LambdaQueryWrapper<V5aiApiKey>()
                .eq(V5aiApiKey::getAgentKey, agentKey));
    }
}
