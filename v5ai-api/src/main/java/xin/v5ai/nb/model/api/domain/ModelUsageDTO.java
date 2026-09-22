package xin.v5ai.nb.model.api.domain;

import java.time.OffsetDateTime;

/**
 * 单次模型调用用量明细（可观测性 / 用量统计）。
 *
 * @param runId             运行 ID
 * @param agentKey            应用标识
 * @param modelId           平台模型配置 ID
 * @param modelKey          上游模型标识快照
 * @param promptTokens      输入 Token（估算）
 * @param completionTokens  输出 Token（估算）
 * @param durationMs        耗时（毫秒）
 * @param status            SUCCESS / FAILED
 * @param createdAt         记录时间
 */
public record ModelUsageDTO(
        Long id,
        /** 运行 ID */
        String runId,
        /** 应用标识 */
        String agentKey,
        /** 平台模型配置 ID；历史/非模型调用可为空 */
        Long modelId,
        /** 上游模型标识快照 */
        String modelKey,
        /** 输入 Token（估算） */
        long promptTokens,
        /** 输出 Token（估算） */
        long completionTokens,
        /** 耗时（毫秒） */
        long durationMs,
        /** 调用状态 */
        String status,
        /** 记录时间 */
        OffsetDateTime createdAt
) {
    /** 总 Token */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
