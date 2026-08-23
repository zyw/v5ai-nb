package xin.v5ai.nb.common.agentscope.core;


import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

/**
 * 由 {@link AgentTextExecutor} 产生的事件（sealed，仅允许固定几种实现）。
 *
 * - Text：文本增量片段，直接流式推送给客户端；
 * - ModelCall：模型调用标记，被上层转换为平台统一的 {@code MODEL_CALL} 事件；
 * - ToolCall：MCP 工具调用标记（携带工具名与权限决策）；
 * - ToolResult：MCP 工具调用结果（成功/失败）。
 */
public sealed interface AgentTextEvent permits AgentTextEvent.Text, AgentTextEvent.ModelCall,
        AgentTextEvent.ToolCall, AgentTextEvent.ToolResult {

    /**
     * 文本增量：Agent 输出的一段文字内容。
     *
     * @param text 文本片段
     */
    record Text(String text) implements AgentTextEvent {
    }

    /**
     * 模型调用标记：标识一次模型调用开始（携带模型名）。
     *
     * @param modelName 本次调用使用的模型名称
     */
    record ModelCall(String modelName) implements AgentTextEvent {
    }

    /**
     * MCP 工具调用标记。
     *
     * @param toolName   工具名
     * @param permission 平台权限决策（ALLOW 直接放行；APPROVE 上层先发 PERMISSION_REQUIRED 事件再放行）
     */
    record ToolCall(String toolName, McpToolPermission permission) implements AgentTextEvent {
    }

    /**
     * MCP 工具调用结果。
     *
     * @param toolName 工具名
     * @param success  是否成功
     * @param message  失败原因（成功时为空串）
     */
    record ToolResult(String toolName, boolean success, String message) implements AgentTextEvent {
    }
}
