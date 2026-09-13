package xin.v5ai.nb.common.agentscope.core;


import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

/**
 * 由 {@link AgentTextExecutor} 产生的事件（sealed，仅允许固定几种实现）。
 *
 * - Reasoning：模型推理（思考）增量片段，与回答分流，被上层转换为 {@code REASONING_DELTA}；
 * - Text：文本增量片段，直接流式推送给客户端；
 * - ModelCall：模型调用标记，被上层转换为平台统一的 {@code MODEL_CALL} 事件；
 * - ToolCall：MCP 工具调用标记（携带工具名与权限决策）；
 * - ToolResult：MCP 工具调用结果（成功/失败）。
 */
public sealed interface AgentTextEvent permits AgentTextEvent.Reasoning, AgentTextEvent.Text,
        AgentTextEvent.ModelCall, AgentTextEvent.ToolCall, AgentTextEvent.ToolResult {

    /**
     * 思考增量：模型输出的推理内容片段（AgentScope {@code ThinkingBlockDeltaEvent} 转来）。
     * 与 {@link Text} 分流，供前端把「思考过程」与「回答」分开展示；上游不落库。
     *
     * @param text 思考片段
     */
    record Reasoning(String text) implements AgentTextEvent {
    }

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
