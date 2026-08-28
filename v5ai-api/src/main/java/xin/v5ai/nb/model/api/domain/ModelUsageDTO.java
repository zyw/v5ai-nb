package xin.v5ai.nb.model.api.domain;

import java.time.OffsetDateTime;

/**
 * 单次模型调用用量明细（可观测性 / 用量统计）。
 *
 * @param runId             运行 ID
 * @param agentKey            应用标识
 * @param modelKey          模型标识
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
        /** 模型标识 */
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
