package xin.v5ai.nb.api.mcp;

import xin.v5ai.nb.mcp.McpToolCallAudit;
import xin.v5ai.nb.mcp.McpToolCallStatus;
import xin.v5ai.nb.mcp.McpToolPermission;

import java.time.Instant;

public record McpToolCallAuditResponse(
        Long id,
        String runId,
        Long serverId,
        String serverName,
        String toolName,
        String argumentsSummary,
        McpToolPermission decision,
        McpToolCallStatus status,
        Long durationMs,
        String message,
        Instant createdAt
) {
    public static McpToolCallAuditResponse from(McpToolCallAudit audit) {
        return new McpToolCallAuditResponse(
                audit.id(),
                audit.runId(),
                audit.serverId(),
                audit.serverName(),
                audit.toolName(),
                audit.argumentsSummary(),
                audit.decision(),
                audit.status(),
                audit.durationMs(),
                audit.message(),
                audit.createdAt());
    }
}
