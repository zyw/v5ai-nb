package xin.v5ai.nb.api.mcp;

import xin.v5ai.nb.mcp.McpToolDefinition;
import xin.v5ai.nb.mcp.McpToolPermission;

import java.time.Instant;
import java.util.Map;

public record McpToolResponse(
        Long id,
        Long serverId,
        String toolName,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly,
        McpToolPermission permission,
        Instant lastDiscoveredAt
) {
    public static McpToolResponse from(McpToolDefinition tool) {
        return new McpToolResponse(
                tool.id(),
                tool.serverId(),
                tool.toolName(),
                tool.description(),
                tool.inputSchema(),
                tool.readOnly(),
                tool.permission(),
                tool.lastDiscoveredAt());
    }
}
