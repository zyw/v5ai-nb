package xin.v5ai.nb.infrastructure.skill;

import java.util.List;

public interface AgentSkillBindingGateway {
    void insertBinding(AgentSkillBindingEntity entity);

    void deleteByAgentKey(String agentKey);

    List<AgentSkillBindingEntity> selectByAgentKey(String agentKey);
}
