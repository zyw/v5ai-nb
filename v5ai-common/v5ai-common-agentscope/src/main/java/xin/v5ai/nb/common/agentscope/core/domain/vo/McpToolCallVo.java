package xin.v5ai.nb.common.agentscope.core.domain.vo;

/**
 * MCP Tool 调用结果（平台统一表示，屏蔽底层协议差异）。
 *
 * @param ok      调用是否成功
 * @param content 成功时的文本内容（可为空串）
 * @param message 失败原因
 */
public record McpToolCallVo(boolean ok, String content, String message) {
    public static McpToolCallVo success(String content) {
        return new McpToolCallVo(true, content == null ? "" : content, null);
    }

    public static McpToolCallVo failure(String message) {
        return new McpToolCallVo(false, null, message == null ? "tool call failed" : message);
    }
}
