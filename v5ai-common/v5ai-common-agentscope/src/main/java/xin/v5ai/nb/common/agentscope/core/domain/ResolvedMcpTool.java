package xin.v5ai.nb.common.agentscope.core.domain;

import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

import java.util.Map;

/**
 * 已解析的 MCP 工具：包含注册到 AgentDTO 所需的元数据、平台权限决策与执行连接。
 *
 * @param serverId    所属 MCP Server
 * @param serverName  MCP Server 名称
 * @param toolName    工具名
 * @param description 工具描述
 * @param inputSchema 输入 JSON Schema（Map 形式）
 * @param permission  平台权限决策
 * @param connection  执行连接（本次运行结束后由执行方关闭）
 */
public record ResolvedMcpTool(
        Long serverId,
        String serverName,
        String toolName,
        String description,
        Map<String, Object> inputSchema,
        McpToolPermission permission,
        McpConnection connection
) {
}
