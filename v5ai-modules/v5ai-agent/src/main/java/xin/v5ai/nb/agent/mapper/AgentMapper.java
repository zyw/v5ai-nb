package xin.v5ai.nb.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;

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
}
