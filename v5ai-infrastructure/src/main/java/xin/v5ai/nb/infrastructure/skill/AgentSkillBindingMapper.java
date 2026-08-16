package xin.v5ai.nb.infrastructure.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentSkillBindingMapper extends BaseMapper<AgentSkillBindingEntity>,
        AgentSkillBindingGateway {
    @Override
    default void insertBinding(AgentSkillBindingEntity entity) {
        insert(entity);
    }

    @Override
    default void deleteByAgentKey(String agentKey) {
        delete(new LambdaQueryWrapper<AgentSkillBindingEntity>()
                .eq(AgentSkillBindingEntity::getAgentKey, agentKey));
    }

    @Override
    default List<AgentSkillBindingEntity> selectByAgentKey(String agentKey) {
        return selectList(new LambdaQueryWrapper<AgentSkillBindingEntity>()
                .eq(AgentSkillBindingEntity::getAgentKey, agentKey));
    }
}
