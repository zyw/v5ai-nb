package xin.v5ai.nb.mcp;

import java.util.List;
import java.util.Map;

/**
 * 创建 MCP Server 的入参。
 *
 * @param name           名称
 * @param transportType  传输类型
 * @param endpoint       HTTP 为 URL；Stdio 为可执行命令
 * @param args           Stdio 参数
 * @param headers        HTTP 请求头
 * @param envVars        Stdio 环境变量
 * @param timeoutSeconds 超时秒数（null 使用默认 30s）
 */
public record CreateMcpServerCommand(
        String name,
        McpTransportType transportType,
        String endpoint,
        List<String> args,
        Map<String, String> headers,
        Map<String, String> envVars,
        Integer timeoutSeconds
) {
}
