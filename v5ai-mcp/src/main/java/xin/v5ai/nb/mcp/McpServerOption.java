package xin.v5ai.nb.mcp;

/**
 * MCP Server 下拉选项的轻量视图（仅需 id 与名称，避免加载端点/加密请求头等重字段）。
 *
 * @param id   主键
 * @param name 平台内唯一标识名
 */
public record McpServerOption(Long id, String name) {
}
