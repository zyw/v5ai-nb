package xin.v5ai.nb.platform.mapper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.platform.domain.V5aiApiKeysAgent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API Key ↔ Agent 绑定（v5ai_api_keys_agent）Mapper：纯关联表，不建 VO。
 */
@Mapper
public interface V5aiApiKeysAgentMapper extends BaseMapper<V5aiApiKeysAgent> {

    /**
     * 查询某把 Key 绑定的 agentKey 列表（按 agent_key 升序）。
     *
     * @param apiKeyId 密钥主键
     * @return 绑定的 agentKey 列表
     */
    default List<String> selectAgentKeysByApiKeyId(Long apiKeyId) {
        return selectList(new LambdaQueryWrapper<V5aiApiKeysAgent>()
                .eq(V5aiApiKeysAgent::getApiKeyId, apiKeyId)
                .orderByAsc(V5aiApiKeysAgent::getAgentKey))
                .stream()
                .map(V5aiApiKeysAgent::getAgentKey)
                .toList();
    }

    /**
     * 批量查询多把 Key 的绑定（列表页一次取完，避免逐行查询）。
     *
     * @param apiKeyIds 密钥主键集合
     * @return apiKeyId → agentKey 列表（无绑定的 Key 不出现在 Map 中）
     */
    default Map<Long, List<String>> selectAgentKeysByApiKeyIds(Collection<Long> apiKeyIds) {
        if (CollUtil.isEmpty(apiKeyIds)) {
            return Map.of();
        }
        var rows = selectList(new LambdaQueryWrapper<V5aiApiKeysAgent>()
                .in(V5aiApiKeysAgent::getApiKeyId, apiKeyIds)
                .orderByAsc(V5aiApiKeysAgent::getApiKeyId)
                .orderByAsc(V5aiApiKeysAgent::getAgentKey));
        return rows.stream().collect(Collectors.groupingBy(V5aiApiKeysAgent::getApiKeyId,
                LinkedHashMap::new,
                Collectors.mapping(V5aiApiKeysAgent::getAgentKey, Collectors.toList())));
    }

    /**
     * 删除某把 Key 的全部绑定（重绑时先清后建）。
     *
     * @param apiKeyId 密钥主键
     * @return 影响行数
     */
    default int deleteByApiKeyId(Long apiKeyId) {
        return delete(new LambdaQueryWrapper<V5aiApiKeysAgent>()
                .eq(V5aiApiKeysAgent::getApiKeyId, apiKeyId));
    }
}