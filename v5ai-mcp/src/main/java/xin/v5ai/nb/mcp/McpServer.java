package xin.v5ai.nb.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 配置（领域模型，不可变）。
 *
 * @param id              主键（新建时为 null）
 * @param name            平台内唯一标识名（也是 MCP 客户端名）
 * @param transportType   传输类型
 * @param endpoint        HTTP 传输为 URL；Stdio 传输为可执行命令
 * @param args            Stdio 命令参数
 * @param headers         HTTP 请求头（明文，持久化层负责加密）
 * @param envVars         Stdio 环境变量（明文，持久化层负责加密）
 * @param timeoutSeconds  请求超时秒数
 * @param status          启用状态
 * @param lastTestStatus  最近一次连接测试结果（ok/failed，可为 null）
 * @param lastTestMessage 最近一次连接测试消息（可为 null）
 * @param lastTestedAt    最近一次连接测试时间（可为 null）
 */
public record McpServer(
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
    public McpServer {
        args = args == null ? List.of() : List.copyOf(args);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        envVars = envVars == null ? Map.of() : Map.copyOf(envVars);
        timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        status = status == null ? McpServerStatus.ACTIVE : status;
    }

    public boolean isActive() {
        return status == McpServerStatus.ACTIVE;
    }

    public McpServer withLastTest(String testStatus, String message, Instant testedAt) {
        return new McpServer(id, name, transportType, endpoint, args, headers, envVars,
                timeoutSeconds, status, testStatus, message, testedAt);
    }

    public McpServer withStatus(McpServerStatus newStatus) {
        return new McpServer(id, name, transportType, endpoint, args, headers, envVars,
                timeoutSeconds, newStatus, lastTestStatus, lastTestMessage, lastTestedAt);
    }
}
