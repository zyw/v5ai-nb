package xin.v5ai.nb.api.mcp;

import xin.v5ai.nb.mcp.McpTransportType;

import java.util.List;
import java.util.Map;

public record CreateMcpServerRequest(
        String name,
        McpTransportType transportType,
        String endpoint,
        List<String> args,
        Map<String, String> headers,
        Map<String, String> envVars,
        Integer timeoutSeconds
) {
}
