package xin.v5ai.nb.common.agentscope.core.reactor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * Reactor 钩子配置：抑制 "Operator called default onErrorDropped" 的 ERROR 级别日志。
 *
 * 背景：当我们的订阅被取消后（超时 / 客户端断开），上游仍在进行中的异步操作
 * （例如 AgentScope 模型内部基于 Java HttpClient 的 future）随后以
 * {@link CancellationException} 结束，Reactor 默认会打印一条 ERROR 日志。
 * 这类取消属于正常现象，不应告警；非取消类错误仍以 WARN 级别记录。
 */
@Slf4j
@Configuration
public class ReactorHooksConfiguration {

    static {
        // 全局设置：被丢弃的错误若属于取消，则静默；否则记录 WARN
        Hooks.onErrorDropped(error -> {
            if (isCancellation(error)) {
                return;
            }
            log.warn("Dropped reactor error", error);
        });
    }

    /**
     * 判断错误是否为"取消"：
     * - 直接是 {@link CancellationException}；
     * - 或是 {@link CompletionException} 包装下的 {@link CancellationException}。
     */
    static boolean isCancellation(Throwable error) {
        if (error instanceof CancellationException) {
            return true;
        }
        Throwable cause = error instanceof CompletionException ? error.getCause() : null;
        return cause instanceof CancellationException;
    }
}
