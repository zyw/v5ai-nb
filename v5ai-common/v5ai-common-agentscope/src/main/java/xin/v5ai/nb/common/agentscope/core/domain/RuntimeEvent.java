package xin.v5ai.nb.common.agentscope.core.domain;

import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;

import java.time.Instant;

/**
 * 运行时事件（不可变 record）：描述一次 Agent 运行的每一步进展，
 * 用于流式推送、前端展示与持久化审计。
 *
 * 创建方式：通过下方静态工厂方法构造，自动填充类型与时间戳。
 *
 * @param runId     所属运行的唯一 ID
 * @param type      事件类型
 * @param payload   事件载荷（文本片段/模型名/错误信息等，无内容时为 ""）
 * @param createdAt 事件产生时间
 */
public record RuntimeEvent(String runId, RuntimeEventType type, String payload, Instant createdAt) {
    /** 运行开始 */
    public static RuntimeEvent started(String runId) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_STARTED, "", Instant.now());
    }

    /** 模型调用开始（携带模型名） */
    public static RuntimeEvent modelCall(String runId, String modelName) {
        return new RuntimeEvent(runId, RuntimeEventType.MODEL_CALL, modelName, Instant.now());
    }

    /** 思考增量片段（模型推理内容，与回答分流，供前端单独展示） */
    public static RuntimeEvent reasoningDelta(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.REASONING_DELTA, payload, Instant.now());
    }

    /** 文本增量片段（用于流式拼装回答） */
    public static RuntimeEvent textDelta(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TEXT_DELTA, payload, Instant.now());
    }

    /** 工具调用（携带调用信息） */
    public static RuntimeEvent toolCall(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TOOL_CALL, payload, Instant.now());
    }

    /** 工具调用结果 */
    public static RuntimeEvent toolResult(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TOOL_RESULT, payload, Instant.now());
    }

    /** 知识检索结果（RAG 上下文） */
    public static RuntimeEvent retrieval(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.RETRIEVAL, payload, Instant.now());
    }

    /** 需要用户授权（如敏感操作） */
    public static RuntimeEvent permissionRequired(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.PERMISSION_REQUIRED, payload, Instant.now());
    }

    /** 一条完整消息生成完毕 */
    public static RuntimeEvent messageCompleted(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.MESSAGE_COMPLETED, payload, Instant.now());
    }

    /** 运行失败（携带错误信息） */
    public static RuntimeEvent failed(String runId, String message) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_FAILED, message, Instant.now());
    }

    /** 运行成功完成 */
    public static RuntimeEvent completed(String runId) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_COMPLETED, "", Instant.now());
    }
}
