package xin.v5ai.nb.infrastructure.mcp;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.mcp.McpToolDefinition;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.McpToolRepository;

import java.time.Instant;
import java.util.List;

@Repository
public class MyBatisMcpToolRepository implements McpToolRepository {
    private final McpToolGateway gateway;

    public MyBatisMcpToolRepository(McpToolGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public McpToolDefinition upsert(McpToolDefinition tool) {
        var existing = gateway.selectToolByServerAndName(tool.serverId(), tool.toolName());
        if (existing == null) {
            var entity = new McpToolEntity();
            entity.setServerId(tool.serverId());
            entity.setToolName(tool.toolName());
            entity.setDescription(tool.description());
            entity.setInputSchemaJson(McpJsonCodec.toJson(tool.inputSchema()));
            entity.setReadOnly(tool.readOnly());
            entity.setPermission(tool.permission().name());
            entity.setLastDiscoveredAt(Instant.now());
            gateway.insertTool(entity);
            return toDomain(entity);
        }
        // 已存在：刷新元数据但保留管理员设置的权限
        existing.setDescription(tool.description());
        existing.setInputSchemaJson(McpJsonCodec.toJson(tool.inputSchema()));
        existing.setReadOnly(tool.readOnly());
        existing.setLastDiscoveredAt(Instant.now());
        gateway.updateTool(existing);
        return toDomain(existing);
    }

    @Override
    public List<McpToolDefinition> findByServerId(Long serverId) {
        return gateway.selectToolsByServer(serverId).stream().map(this::toDomain).toList();
    }

    @Override
    public McpToolDefinition findByServerAndName(Long serverId, String toolName) {
        return toDomain(gateway.selectToolByServerAndName(serverId, toolName));
    }

    @Override
    public void updatePermission(Long serverId, String toolName, McpToolPermission permission) {
        gateway.updateToolPermission(serverId, toolName, permission.name());
    }

    @Override
    public void deleteByServerId(Long serverId) {
        gateway.deleteToolsByServer(serverId);
    }

    @Override
    public void deleteByServerIdNotIn(Long serverId, List<String> keepToolNames) {
        gateway.deleteToolsByServerNotIn(serverId, keepToolNames);
    }

    private McpToolDefinition toDomain(McpToolEntity entity) {
        if (entity == null) {
            return null;
        }
        return new McpToolDefinition(
                entity.getId(),
                entity.getServerId(),
                entity.getToolName(),
                entity.getDescription(),
                McpJsonCodec.toObjectMap(entity.getInputSchemaJson()),
                Boolean.TRUE.equals(entity.getReadOnly()),
                McpToolPermission.valueOf(entity.getPermission()),
                entity.getLastDiscoveredAt());
    }
}
