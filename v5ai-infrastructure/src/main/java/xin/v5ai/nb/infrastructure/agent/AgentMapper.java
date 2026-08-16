package xin.v5ai.nb.infrastructure.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity>, AgentGateway {
    @Override
    default void insertAgent(AgentEntity entity) {
        insert(entity);
    }

    @Override
    default AgentEntity selectByAgentKey(String agentKey) {
        return selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentKey, agentKey));
    }

    @Override
    default List<AgentEntity> selectAgents() {
        return selectList(null);
    }

    @Override
    default void updateAgent(AgentEntity entity) {
        updateById(entity);
    }

    @Override
    default void disable(String agentKey) {
        update(new LambdaUpdateWrapper<AgentEntity>()
                .eq(AgentEntity::getAgentKey, agentKey)
                .set(AgentEntity::getStatus, "DISABLED"));
    }

    @Override
    default void updatePublished(String agentKey, String status, Long publishedVersion) {
        update(new LambdaUpdateWrapper<AgentEntity>()
                .eq(AgentEntity::getAgentKey, agentKey)
                .set(AgentEntity::getStatus, status)
                .set(AgentEntity::getPublishedVersion, publishedVersion));
    }
}
