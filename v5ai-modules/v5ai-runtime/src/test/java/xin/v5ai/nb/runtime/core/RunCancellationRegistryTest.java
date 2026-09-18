package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 取消登记处：按 runId 持有取消信号，停止端点靠它打断同实例上正在跑的运行。
 */
class RunCancellationRegistryTest {

    @Test
    void cancelOfAnUnknownRunIsAFalseResultNotAnError() {
        var registry = new RunCancellationRegistry();

        assertThat(registry.cancel("never-registered")).isFalse();
        assertThat(registry.cancel(null)).isFalse();
        assertThatCode(() -> registry.unregister(null)).doesNotThrowAnyException();
    }

    @Test
    void registeredSignalIsTriggeredAndRepeatCancelStaysHarmless() {
        var registry = new RunCancellationRegistry();
        var signal = Sinks.<Void>empty();
        registry.register("run-1", signal);

        assertThat(registry.cancel("run-1")).isTrue();
        assertThat(signal.asMono().block()).isNull();
        // 幂等：信号已终止，重复触发既不报错也不改变结果
        assertThatCode(() -> registry.cancel("run-1")).doesNotThrowAnyException();
    }

    @Test
    void unregisteredRunCanNoLongerBeCanceled() {
        var registry = new RunCancellationRegistry();
        registry.register("run-1", Sinks.empty());
        registry.unregister("run-1");

        assertThat(registry.cancel("run-1")).isFalse();
    }
}
