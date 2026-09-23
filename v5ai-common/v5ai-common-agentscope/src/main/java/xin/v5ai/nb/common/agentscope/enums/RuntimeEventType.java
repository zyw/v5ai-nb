package xin.v5ai.nb.common.agentscope.enums;

/**
 * 运行时事件类型：完整描述一次 AgentDTO 运行的每个阶段。
 */
public enum RuntimeEventType {
    /** 运行开始 */
    RUN_STARTED,
    /** 模型调用开始 */
    MODEL_CALL,
    /**
     * 模型用量（输入 / 输出 token，真实值或平台估算）。
     *
     * <p><b>仅用于平台内部传递</b>：由执行器发出、{@code PersistingAgentRuntime} 消费后过滤掉，
     * 既不落 {@code v5ai_run_event}，也不会出现在 SSE 流里。对外可见的用量仍是
     * {@code RUN_COMPLETED} 的载荷（与助手消息、用量明细同源）。</p>
     */
    MODEL_USAGE,
    /** 思考增量（模型推理内容，与回答分流推送） */
    REASONING_DELTA,
    /** 文本增量（回答片段） */
    TEXT_DELTA,
    /** 工具调用 */
    TOOL_CALL,
    /** 工具调用结果 */
    TOOL_RESULT,
    /** 知识检索（RAG） */
    RETRIEVAL,
    /** 需要用户授权 */
    PERMISSION_REQUIRED,
    /** 单条消息生成完成 */
    MESSAGE_COMPLETED,
    /** 运行失败 */
    RUN_FAILED,
    /** 运行成功完成 */
    RUN_COMPLETED
}
