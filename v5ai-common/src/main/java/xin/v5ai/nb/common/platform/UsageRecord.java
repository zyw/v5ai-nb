package xin.v5ai.nb.common.platform;

import java.time.Instant;

/**
 * 单次模型调用用量明细（可观测性 / 用量统计）。
 *
 * @param runId             运行 ID
 * @param appKey            应用标识
 * @param modelKey          模型标识
 * @param promptTokens      输入 Token（估算）
 * @param completionTokens  输出 Token（估算）
 * @param durationMs        耗时（毫秒）
 * @param status            SUCCESS / FAILED
 * @param createdAt         记录时间
 */
public record UsageRecord(
        Long id,
        String runId,
        String appKey,
        String modelKey,
        long promptTokens,
        long completionTokens,
        long durationMs,
        String status,
        Instant createdAt
) {
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
