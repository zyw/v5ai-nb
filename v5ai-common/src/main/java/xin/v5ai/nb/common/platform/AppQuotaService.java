package xin.v5ai.nb.common.platform;

/**
 * 应用配额与用量服务：调用前检查（配额 + 限流），完成后记账。
 */
public interface AppQuotaService {

    /** 查询应用配额（不存在时返回全 0 = 不限）。 */
    AppQuota getQuota(String agentKey);

    /** 更新应用配额（null 字段保持原值）。 */
    AppQuota updateQuota(String agentKey, Integer dailyModelCalls, Long dailyTokens, Integer ratePerMinute);

    /**
     * 运行请求放行检查：今日调用次数配额 + 每分钟限流。
     *
     * @return 允许则 true；超配额/超限流返回 false
     */
    boolean checkAllowed(String agentKey);

    /** 运行完成后记账：递增今日调用次数与 Token 用量。 */
    void recordUsage(String agentKey, long tokens);

    /** 应用当日用量（调用次数 + Token）。 */
    AppUsage getDailyUsage(String agentKey);
}
