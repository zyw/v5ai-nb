package xin.v5ai.nb.mcp.controller;

/**
 * MCP Server 连接测试响应体。
 *
 * @param serverId  Server ID
 * @param ok        是否成功
 * @param message   结果消息
 * @param toolCount 发现到的工具数
 */
public record McpConnectionTestResponse(Long serverId, boolean ok, String message, int toolCount) {
}
