package xin.v5ai.nb.mcp;

import java.util.List;
import java.util.Map;

/**
 * 更新 MCP Server 的入参（字段为 null 表示保持原值）。
 */
public record UpdateMcpServerCommand(
        Long id,
        String name,
        McpTransportType transportType,
        String endpoint,
        List<String> args,
        Map<String, String> headers,
        Map<String, String> envVars,
        Integer timeoutSeconds
) {
}
