package xin.v5ai.nb.api.mcp;

import xin.v5ai.nb.mcp.McpConnectionTestResult;

public record McpConnectionTestResponse(Long serverId, boolean ok, String message, int toolCount) {
    public static McpConnectionTestResponse from(Long serverId, McpConnectionTestResult result) {
        return new McpConnectionTestResponse(serverId, result.ok(), result.message(), result.toolCount());
    }
}
