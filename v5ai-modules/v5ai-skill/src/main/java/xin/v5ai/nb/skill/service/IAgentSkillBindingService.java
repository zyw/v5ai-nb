package xin.v5ai.nb.skill.service;

import xin.v5ai.nb.skill.domain.vo.AgentSkillBindingVo;

import java.util.List;

public interface IAgentSkillBindingService {
    /**
     * 查询某 Agent 绑定的全部 Skill。
     *
     * @param agentKey Agent 对外标识
     * @return 绑定列表
     */
    List<AgentSkillBindingVo> selectList(String agentKey);
}
