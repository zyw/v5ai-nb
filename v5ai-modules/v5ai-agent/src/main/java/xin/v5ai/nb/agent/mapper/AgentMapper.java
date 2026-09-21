package xin.v5ai.nb.agent.mapper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
     * 清除次要模型（回退为绑定的对话模型）。
     *
     * <p><b>为什么要单独一条语句</b>：MyBatis-Plus 全局 {@code updateStrategy: NOT_NULL}
     * （见 {@code common-mybatis.yml}），走 {@code updateById(entity)} 时 {@code null} 字段会被
     * 跳过而不写入——想把列改回 {@code NULL} 只能显式 {@code set}。</p>
     *
     * <p>不能改用 {@code @TableField(updateStrategy = ALWAYS)} 让实体更新写 null：
     * {@code disable()} 与 {@code publish()} 都是「只填 id + 改动字段」的部分实体更新，
     * ALWAYS 会让那两处把 {@code secondary_model_id} 一并抹成 NULL。</p>
     *
     * @param id Agent 主键
     * @return 受影响行数
     */
    default int clearSecondaryModel(Long id) {
        return update(null, new LambdaUpdateWrapper<Agent>()
                .eq(Agent::getId, id)
                .set(Agent::getSecondaryModelId, null));
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
