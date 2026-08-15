package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface McpToolCallAuditMapper extends BaseMapper<McpToolCallAuditEntity>, McpToolCallAuditGateway {
    @Override
    default void insertAudit(McpToolCallAuditEntity entity) {
        insert(entity);
    }

    @Override
    default List<McpToolCallAuditEntity> selectByRunId(String runId) {
        return selectList(new LambdaQueryWrapper<McpToolCallAuditEntity>()
                .eq(McpToolCallAuditEntity::getRunId, runId)
                .orderByAsc(McpToolCallAuditEntity::getId));
    }

    @Override
    default List<McpToolCallAuditEntity> selectByServerId(Long serverId) {
        return selectList(new LambdaQueryWrapper<McpToolCallAuditEntity>()
                .eq(McpToolCallAuditEntity::getServerId, serverId)
                .orderByDesc(McpToolCallAuditEntity::getId));
    }
}
