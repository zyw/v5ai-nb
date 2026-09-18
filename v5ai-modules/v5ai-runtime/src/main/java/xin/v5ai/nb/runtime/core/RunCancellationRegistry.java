package xin.v5ai.nb.runtime.core;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进行中运行的取消登记处：按 runId 持有本次运行的取消信号，供「停止对话」端点触发。
 *
 * <p>运行开始（收到 RUN_STARTED）时登记、流终止时注销。停止端点在**同一个实例**里
 * 找到信号才会真正打断：多实例部署时 stop 请求必须落到跑着这次运行的那个实例
 * （与既有 {@code InMemorySlidingWindowRateLimiter} 是同一限制）。</p>
 */
@Component
public class RunCancellationRegistry {

    private final Map<String, Sinks.Empty<Void>> signals = new ConcurrentHashMap<>();

    /**
     * 登记一次运行的取消信号。
     *
     * @param runId  运行 ID
     * @param signal 该运行的取消信号（订阅方用 {@code takeUntilOther} 挂上）
     */
    public void register(String runId, Sinks.Empty<Void> signal) {
        if (runId != null) {
            signals.put(runId, signal);
        }
    }

    /**
     * 注销：流无论以何种方式终止都必须调用，否则信号会随运行数无限堆积。
     */
    public void unregister(String runId) {
        if (runId != null) {
            signals.remove(runId);
        }
    }

    /**
     * 请求取消一次运行；幂等（重复调用只是重复发同一个信号）。
     *
     * @param runId 运行 ID
     * @return true 表示本实例正跑着这次运行并已发出取消信号；false 表示找不到（已结束或不在本实例）
     */
    public boolean cancel(String runId) {
        var signal = runId == null ? null : signals.get(runId);
        if (signal == null) {
            return false;
        }
        signal.tryEmitEmpty();
        return true;
    }
}
