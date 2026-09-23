package xin.v5ai.nb.common.agentscope.core.domain.dto;

import cn.hutool.json.JSONUtil;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;

import java.time.Instant;

/**
 * 运行时事件（不可变 record）：描述一次 AgentDTO 运行的每一步进展，
 * 用于流式推送、前端展示与持久化审计。
 *
 * 创建方式：通过下方静态工厂方法构造，自动填充类型与时间戳。
 *
 * @param runId     所属运行的唯一 ID
 * @param type      事件类型
 * @param payload   事件载荷（文本片段/模型名/错误信息等，无内容时为 ""）
 * @param createdAt 事件产生时间
 */
public record RuntimeRunEventDTO(String runId, RuntimeEventType type, String payload, Instant createdAt) {
    /**
     * 运行开始，并携带本次运行的上下文。
     *
     * <p>载荷为 JSON：{@code {"userMessageId":n}} —— 本轮提问落库后的主键。前端据此在
     * **不刷新历史**的情况下也能「重新生成」刚生成的那一轮（重新生成以该轮提问为锚点）。</p>
     */
    public static RuntimeRunEventDTO started(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.RUN_STARTED,
                payload == null ? "" : payload, Instant.now());
    }

    /** 运行开始（不带上下文载荷） */
    public static RuntimeRunEventDTO started(String runId) {
        return started(runId, "");
    }

    /** 模型调用开始（兼容旧调用方，仅携带模型名） */
    public static RuntimeRunEventDTO modelCall(String runId, String modelName) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.MODEL_CALL, modelName, Instant.now());
    }

    /** 模型调用开始：模型名继续作为外部可读信息，模型 ID 作为内部用量归因信息。 */
    public static RuntimeRunEventDTO modelCall(String runId, Long modelId, String modelName) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.MODEL_CALL,
                JSONUtil.createObj().set("modelId", modelId).set("modelName", modelName).toString(), Instant.now());
    }

    public static RuntimeRunEventDTO modelCall(String runId, Long modelId, String modelKey, String modelName) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.MODEL_CALL,
                JSONUtil.createObj().set("modelId", modelId).set("modelKey", modelKey)
                        .set("modelName", modelName).toString(), Instant.now());
    }

    /**
     * 模型用量（**内部事件**：不落库、不推送给客户端）。
     *
     * <p>载荷为 JSON：{@code {"inputTokens":n,"outputTokens":n,"estimated":bool}}。一次运行最多
     * 两类：开头的估算（{@code estimated=true}）与每次模型调用结束后的真实值（{@code estimated=false}，
     * 多轮工具调用会有多条）。持久化侧按「真实优先、估算兜底」取用。</p>
     */
    public static RuntimeRunEventDTO modelUsage(String runId, long inputTokens, long outputTokens,
                                                boolean estimated) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.MODEL_USAGE,
                JSONUtil.createObj()
                        .set("inputTokens", inputTokens)
                        .set("outputTokens", outputTokens)
                        .set("estimated", estimated)
                        .toString(),
                Instant.now());
    }

    /** 思考增量片段（模型推理内容，与回答分流，供前端单独展示） */
    public static RuntimeRunEventDTO reasoningDelta(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.REASONING_DELTA, payload, Instant.now());
    }

    /** 文本增量片段（用于流式拼装回答） */
    public static RuntimeRunEventDTO textDelta(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.TEXT_DELTA, payload, Instant.now());
    }

    /** 工具调用（携带调用信息） */
    public static RuntimeRunEventDTO toolCall(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.TOOL_CALL, payload, Instant.now());
    }

    /** 工具调用结果 */
    public static RuntimeRunEventDTO toolResult(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.TOOL_RESULT, payload, Instant.now());
    }

    /** 知识检索结果（RAG 上下文） */
    public static RuntimeRunEventDTO retrieval(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.RETRIEVAL, payload, Instant.now());
    }

    /** 需要用户授权（如敏感操作） */
    public static RuntimeRunEventDTO permissionRequired(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.PERMISSION_REQUIRED, payload, Instant.now());
    }

    /** 一条完整消息生成完毕 */
    public static RuntimeRunEventDTO messageCompleted(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.MESSAGE_COMPLETED, payload, Instant.now());
    }

    /** 运行失败（携带错误信息） */
    public static RuntimeRunEventDTO failed(String runId, String message) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.RUN_FAILED, message, Instant.now());
    }

    /**
     * 运行成功完成，并携带本次运行的用量汇总。
     *
     * <p>载荷为 JSON：{@code {"promptTokens":n,"completionTokens":n,"totalTokens":n,"durationMs":n}}。
     * token 是平台按字符数估算的值（与 {@code v5ai_model_usage} 记账、配额扣减同一套数字），
     * 前端展示「用量/用时」直接读它，避免再自己估一份。</p>
     */
    public static RuntimeRunEventDTO completed(String runId, String payload) {
        return new RuntimeRunEventDTO(runId, RuntimeEventType.RUN_COMPLETED,
                payload == null ? "" : payload, Instant.now());
    }

    /** 运行成功完成（不带用量载荷，如知识库问答调试链路） */
    public static RuntimeRunEventDTO completed(String runId) {
        return completed(runId, "");
    }
}
