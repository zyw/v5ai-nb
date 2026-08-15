package xin.v5ai.nb.mcp;

import java.time.Instant;
import java.util.Map;

/**
 * MCP Tool 元数据（缓存自 Tool 发现结果）。
 *
 * @param id               主键（新建时为 null）
 * @param serverId         所属 MCP Server
 * @param toolName         工具名（MCP 协议内唯一）
 * @param description      工具描述
 * @param inputSchema      输入 JSON Schema（Map 形式）
 * @param readOnly         服务器声明的只读提示
 * @param permission       平台权限决策（管理员可调整）
 * @param lastDiscoveredAt 最近一次发现时间
 */
public record McpToolDefinition(
        Long id,
        Long serverId,
        String toolName,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly,
        McpToolPermission permission,
        Instant lastDiscoveredAt
) {
    public McpToolDefinition {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        permission = permission == null ? McpToolPermission.ALLOW : permission;
    }

    public McpToolDefinition withPermission(McpToolPermission newPermission) {
        return new McpToolDefinition(id, serverId, toolName, description, inputSchema, readOnly, newPermission, lastDiscoveredAt);
    }
}
