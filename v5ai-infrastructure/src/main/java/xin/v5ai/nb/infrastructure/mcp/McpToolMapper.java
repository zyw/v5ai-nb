package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface McpToolMapper extends BaseMapper<McpToolEntity>, McpToolGateway {
    @Override
    default void insertTool(McpToolEntity entity) {
        insert(entity);
    }

    @Override
    default void updateTool(McpToolEntity entity) {
        updateById(entity);
    }

    @Override
    default McpToolEntity selectToolById(Long id) {
        return selectById(id);
    }

    @Override
    default McpToolEntity selectToolByServerAndName(Long serverId, String toolName) {
        return selectOne(new LambdaQueryWrapper<McpToolEntity>()
                .eq(McpToolEntity::getServerId, serverId)
                .eq(McpToolEntity::getToolName, toolName));
    }

    @Override
    default List<McpToolEntity> selectToolsByServer(Long serverId) {
        return selectList(new LambdaQueryWrapper<McpToolEntity>()
                .eq(McpToolEntity::getServerId, serverId)
                .orderByAsc(McpToolEntity::getToolName));
    }

    @Override
    default void deleteToolsByServer(Long serverId) {
        delete(new LambdaQueryWrapper<McpToolEntity>().eq(McpToolEntity::getServerId, serverId));
    }

    @Override
    default void deleteToolsByServerNotIn(Long serverId, List<String> keepToolNames) {
        delete(new LambdaQueryWrapper<McpToolEntity>()
                .eq(McpToolEntity::getServerId, serverId)
                .notIn(!keepToolNames.isEmpty(), McpToolEntity::getToolName, keepToolNames));
    }

    @Override
    default void updateToolPermission(Long serverId, String toolName, String permission) {
        var entity = new McpToolEntity();
        entity.setPermission(permission);
        update(entity, new LambdaUpdateWrapper<McpToolEntity>()
                .eq(McpToolEntity::getServerId, serverId)
                .eq(McpToolEntity::getToolName, toolName));
    }
}
