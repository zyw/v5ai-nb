package xin.v5ai.nb.mcp.controller;

import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

/**
 * MCP Tool 权限调整请求体。
 *
 * @param permission 平台权限决策（ALLOW / APPROVE / DENY）
 */
public record McpToolPermissionRequest(McpToolPermission permission) {
}
