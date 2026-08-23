package xin.v5ai.nb.mcp.core.domain;

/**
 * MCP 连接测试结果。
 *
 * @param ok        是否成功
 * @param message   失败原因 / 成功信息
 * @param toolCount 成功连接后发现的工具数量（失败时为 0）
 */
public record McpConnectionTestResult(boolean ok, String message, int toolCount) {
    public static McpConnectionTestResult success(int toolCount) {
        return new McpConnectionTestResult(true, "connection ok", toolCount);
    }

    public static McpConnectionTestResult failure(String message) {
        return new McpConnectionTestResult(false, message, 0);
    }
}
