package xin.v5ai.nb.common.agentscope.core.domain;

import java.util.Map;

/**
 * 连接 MCP Server 后发现的单个 Tool 元数据（与传输层解耦的平台表示）。
 *
 * @param name        工具名
 * @param description 工具描述
 * @param inputSchema 输入 JSON Schema（Map 形式）
 * @param readOnlyHint 服务器声明的只读提示（决定默认权限）
 */
public record McpToolInfo(
        String name,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnlyHint
) {
}
