package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentMcpBindingMapper extends BaseMapper<AgentMcpBindingEntity>,
        AgentMcpBindingGateway {
    @Override
    default void insertBinding(AgentMcpBindingEntity entity) {
        insert(entity);
    }

    @Override
    default void deleteByAgentKey(String agentKey) {
        delete(new LambdaQueryWrapper<AgentMcpBindingEntity>()
                .eq(AgentMcpBindingEntity::getAgentKey, agentKey));
    }

    @Override
    default List<AgentMcpBindingEntity> selectByAgentKey(String agentKey) {
        return selectList(new LambdaQueryWrapper<AgentMcpBindingEntity>()
                .eq(AgentMcpBindingEntity::getAgentKey, agentKey));
    }
}
