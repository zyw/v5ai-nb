package xin.v5ai.nb.skill;

import java.util.List;

public interface AgentSkillBindingRepository {
    /** 全量替换某 Agent 绑定的 Skill 集合。 */
    void save(String agentKey, List<Long> skillIds);

    List<Long> findByAgentKey(String agentKey);

    void delete(String agentKey);
}
