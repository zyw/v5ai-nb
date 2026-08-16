package xin.v5ai.nb.infrastructure.skill;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.skill.AgentSkillBindingRepository;

import java.util.List;

@Repository
public class MyBatisAgentSkillBindingRepository implements AgentSkillBindingRepository {
    private final AgentSkillBindingGateway gateway;

    public MyBatisAgentSkillBindingRepository(AgentSkillBindingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String agentKey, List<Long> skillIds) {
        gateway.deleteByAgentKey(agentKey);
        for (Long skillId : skillIds) {
            var entity = new AgentSkillBindingEntity();
            entity.setAgentKey(agentKey);
            entity.setSkillId(skillId);
            gateway.insertBinding(entity);
        }
    }

    @Override
    public List<Long> findByAgentKey(String agentKey) {
        return gateway.selectByAgentKey(agentKey).stream()
                .map(AgentSkillBindingEntity::getSkillId)
                .toList();
    }

    @Override
    public void delete(String agentKey) {
        gateway.deleteByAgentKey(agentKey);
    }
}
