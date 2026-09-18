package xin.v5ai.nb.agent.mapper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * AgentDTO Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface AgentMapper extends BaseMapperPlus<Agent, AgentVo> {

    /**
     * 按 agentKey 查询 AgentDTO。
     *
     * @param agentKey 对外运行标识
     * @return 匹配的 AgentDTO，不存在时返回 null
     */
    default Agent selectByAgentKey(String agentKey) {
        return selectOne(new LambdaQueryWrapper<Agent>().eq(Agent::getAgentKey, agentKey));
    }

    /**
     * 从入参 agentKey 中筛出 PUBLISHED 状态的子集（供 API Key 绑定等写入路径校验）。
     *
     * @param agentKeys 待校验的 agentKey 集合
     * @return 其中已发布的 agentKey 集合
     */
    default Set<String> selectPublishedKeys(Collection<String> agentKeys) {
        if (CollUtil.isEmpty(agentKeys)) {
            return Set.of();
        }
        return selectList(new LambdaQueryWrapper<Agent>()
                .select(Agent::getAgentKey)
                .eq(Agent::getStatus, AgentStatus.PUBLISHED.name())
                .in(Agent::getAgentKey, agentKeys))
                .stream()
                .map(Agent::getAgentKey)
                .collect(Collectors.toSet());
    }
}
