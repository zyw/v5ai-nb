package xin.v5ai.nb.common.agentscope.enums;

/**
 * 运行时事件类型：完整描述一次 AgentDTO 运行的每个阶段。
 */
public enum RuntimeEventType {
    /** 运行开始 */
    RUN_STARTED,
    /** 模型调用开始 */
    MODEL_CALL,
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
