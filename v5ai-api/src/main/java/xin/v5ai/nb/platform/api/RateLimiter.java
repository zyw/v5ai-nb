package xin.v5ai.nb.platform.api;

/**
 * 限流器：按 key 维护滑动窗口，用于运行 API 的按应用限流。
 */
public interface RateLimiter {

    /**
     * 尝试获取 1 个许可。
     *
     * @param key          限流维度（如 agentKey）
     * @param permitsPerMinute 每分钟许可上限（0 表示不限）
     * @return true 放行；false 超出限流
     */
    boolean tryAcquire(String key, int permitsPerMinute);
}
