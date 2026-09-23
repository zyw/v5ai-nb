package xin.v5ai.nb.platform.service.impl;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.platform.api.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内滑动窗口限流器：按 key 维护固定槽位的最近请求时间戳。
 * 单实例部署够用；多实例需换 Redis 实现。
 */
@Component
public class InMemorySlidingWindowRateLimiter implements RateLimiter {
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int permitsPerMinute) {
        if (permitsPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long[] timestamps = windows.computeIfAbsent(key, k -> new long[permitsPerMinute]);
        synchronized (timestamps) {
            // 1) 优先占用空槽
            for (int i = 0; i < timestamps.length; i++) {
                if (timestamps[i] == 0) {
                    timestamps[i] = now;
                    return true;
                }
            }
            // 2) 无空槽：替换最旧的已超窗时间戳
            int oldestSlot = -1;
            long oldest = Long.MAX_VALUE;
            for (int i = 0; i < timestamps.length; i++) {
                if (now - timestamps[i] > WINDOW_MILLIS && timestamps[i] < oldest) {
                    oldestSlot = i;
                    oldest = timestamps[i];
                }
            }
            if (oldestSlot >= 0) {
                timestamps[oldestSlot] = now;
                return true;
            }
            // 3) 窗口内已占满
            return false;
        }
    }
}
