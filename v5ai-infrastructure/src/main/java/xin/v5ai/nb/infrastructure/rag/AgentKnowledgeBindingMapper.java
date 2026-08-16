package xin.v5ai.nb.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentKnowledgeBindingMapper extends BaseMapper<AgentKnowledgeBindingEntity>,
        AgentKnowledgeBindingGateway {
    @Override
    default void insertBinding(AgentKnowledgeBindingEntity entity) {
        insert(entity);
    }

    @Override
    default void deleteByAgentKey(String agentKey) {
        delete(new LambdaQueryWrapper<AgentKnowledgeBindingEntity>()
                .eq(AgentKnowledgeBindingEntity::getAgentKey, agentKey));
    }

    @Override
    default List<AgentKnowledgeBindingEntity> selectByAgentKey(String agentKey) {
        return selectList(new LambdaQueryWrapper<AgentKnowledgeBindingEntity>()
                .eq(AgentKnowledgeBindingEntity::getAgentKey, agentKey));
    }
}
