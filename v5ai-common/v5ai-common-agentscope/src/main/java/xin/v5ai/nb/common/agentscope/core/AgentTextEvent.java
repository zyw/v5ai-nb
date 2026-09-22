package xin.v5ai.nb.common.agentscope.core;


import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

import java.util.List;

/**
 * 由 {@link AgentTextExecutor} 产生的事件（sealed，仅允许固定几种实现）。
 *
 * - Reasoning：模型推理（思考）增量片段，与回答分流，被上层转换为 {@code REASONING_DELTA}；
 * - Text：文本增量片段，直接流式推送给客户端；
 * - ModelCall：模型调用标记，被上层转换为平台统一的 {@code MODEL_CALL} 事件；
 * - ToolCall：MCP 工具调用标记（携带工具名与权限决策）；
 * - ToolResult：MCP 工具调用结果（成功/失败）；
 * - Retrieval：RAG 智能检索命中（{@code rag_search} 工具调用后），被上层转换为 {@code RETRIEVAL} 事件；
 * - Usage：本次运行的模型用量（真实回报或平台估算），**只用于平台内部传递**（供持久化侧记账），
 *   不落库、不推送给客户端。
 */
public sealed interface AgentTextEvent permits AgentTextEvent.Reasoning, AgentTextEvent.Text,
        AgentTextEvent.ModelCall, AgentTextEvent.ToolCall, AgentTextEvent.ToolResult, AgentTextEvent.Retrieval,
        AgentTextEvent.Usage {

    /**
     * 思考增量：模型输出的推理内容片段（AgentScope {@code ThinkingBlockDeltaEvent} 转来）。
     * 与 {@link Text} 分流，供前端把「思考过程」与「回答」分开展示；上游不落库。
     *
     * @param text 思考片段
     */
    record Reasoning(String text) implements AgentTextEvent {
    }

    /**
     * 文本增量：AgentDTO 输出的一段文字内容。
     *
     * @param text 文本片段
     */
    record Text(String text) implements AgentTextEvent {
    }

    /**
     * 模型调用标记：标识一次模型调用开始（携带平台模型 ID、上游 key 和展示名称）。
     *
     * @param modelId   本次调用使用的平台模型配置 ID
     * @param modelKey  本次调用使用的上游模型 key
     * @param modelName 本次调用使用的模型名称
     */
    record ModelCall(Long modelId, String modelKey, String modelName) implements AgentTextEvent {
        public ModelCall(String modelName) {
            this(null, null, modelName);
        }
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

    /**
     * RAG 检索命中：智能调用模式下 {@code rag_search} 工具执行后产出，
     * 上层转为 {@code RETRIEVAL} 事件（与强制调用 pre-step 的引用事件同口径）。
     *
     * @param hits 结构化命中（可为空列表）
     */
    record Retrieval(List<RagHit> hits) implements AgentTextEvent {
    }

    /**
     * 模型用量（输入 / 输出 token）。
     *
     * <p>一次运行最多两种：开头一条 {@code estimated=true} 的「我准备发这么多上下文」估算，
     * 以及每次模型调用结束后一条 {@code estimated=false} 的真实值（多轮工具调用时会有多条，
     * 由持久化侧累加）。持久化侧取「真实优先、估算兜底」。</p>
     *
     * @param inputTokens  输入 token（提示词侧）
     * @param outputTokens 输出 token（生成侧；估算事件里为 0）
     * @param estimated    true=平台按字符估算，false=模型回报的真实值
     */
    record Usage(long inputTokens, long outputTokens, boolean estimated) implements AgentTextEvent {
    }
}
