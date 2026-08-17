package xin.v5ai.nb.common.core.platform;

import java.time.Instant;

/**
 * 应用配额：0 表示不限制。
 *
 * @param agentKey            应用标识
 * @param dailyModelCalls   每日模型调用次数上限（0 不限）
 * @param dailyTokens       每日 Token 用量上限（0 不限）
 * @param ratePerMinute     每分钟请求限流上限（0 不限）
 * @param updatedAt         更新时间
 */
public record AppQuota(
        String agentKey,
        int dailyModelCalls,
        long dailyTokens,
        int ratePerMinute,
        Instant updatedAt
) {
}
