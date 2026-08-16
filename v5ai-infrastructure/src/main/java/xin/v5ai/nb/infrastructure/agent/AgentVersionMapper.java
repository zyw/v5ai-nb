package xin.v5ai.nb.infrastructure.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionEntity>, AgentVersionGateway {
    @Override
    default void insertVersion(AgentVersionEntity entity) {
        insert(entity);
    }

    @Override
    default long countVersions(String agentKey) {
        return selectCount(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentKey, agentKey));
    }

    @Override
    default List<AgentVersionEntity> selectVersions(String agentKey) {
        return selectList(new LambdaQueryWrapper<AgentVersionEntity>()
                .eq(AgentVersionEntity::getAgentKey, agentKey)
                .orderByDesc(AgentVersionEntity::getVersion));
    }
}
