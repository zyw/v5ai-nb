package xin.v5ai.nb.api.mcp;

import xin.v5ai.nb.mcp.McpServer;
import xin.v5ai.nb.mcp.McpServerStatus;
import xin.v5ai.nb.mcp.McpTransportType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record McpServerResponse(
        Long id,
        String name,
        McpTransportType transportType,
        String endpoint,
        List<String> args,
        Map<String, String> headers,
        Map<String, String> envVars,
        Integer timeoutSeconds,
        McpServerStatus status,
        String lastTestStatus,
        String lastTestMessage,
        Instant lastTestedAt
) {
    public static McpServerResponse from(McpServer server) {
        return new McpServerResponse(
                server.id(),
                server.name(),
                server.transportType(),
                server.endpoint(),
                server.args(),
                server.headers(),
                server.envVars(),
                server.timeoutSeconds(),
                server.status(),
                server.lastTestStatus(),
                server.lastTestMessage(),
                server.lastTestedAt());
    }
}
