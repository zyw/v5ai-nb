package xin.v5ai.nb.starter.controller.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * API Key 维度的用量统计：汇总排行 + 按日趋势。
 */
public record ApiKeyUsageStatsResponse(
        List<Summary> summaries,
        List<Daily> daily
) {
    public record Summary(
            Long apiKeyId,
            String apiKeyName,
            String trackingId,
            long modelCalls,
            long totalTokens,
            long promptTokens,
            long completionTokens,
            long successCalls,
            long failedCalls,
            long runCount,
            long conversationCount,
            long averageDurationMs
    ) {
    }

    public record Daily(
            LocalDate usageDate,
            long modelCalls,
            long totalTokens,
            long successCalls,
            long failedCalls
    ) {
    }
}
