package xin.v5ai.nb.mcp.controller;

import java.util.List;

/**
 * AgentDTO 绑定 MCP Server 请求体。
 *
 * @param mcpServerIds 要绑定的 MCP Server ID 集合（全量替换）
 */
public record McpBindRequest(List<Long> mcpServerIds) {
}
