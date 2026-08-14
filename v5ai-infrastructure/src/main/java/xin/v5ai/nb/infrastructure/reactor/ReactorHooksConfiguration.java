package xin.v5ai.nb.infrastructure.reactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * Silences Reactor's "Operator called default onErrorDropped" ERROR log when an
 * in-flight upstream (e.g. a Java HttpClient future inside an AgentScope model)
 * completes with {@link CancellationException} after our subscription was cancelled
 * (timeout / client disconnect). Such cancellations are normal; non-cancellation
 * drops are still logged at WARN.
 */
@Configuration
public class ReactorHooksConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(ReactorHooksConfiguration.class);

    static {
        Hooks.onErrorDropped(error -> {
            if (isCancellation(error)) {
                return;
            }
            LOG.warn("Dropped reactor error", error);
        });
    }

    static boolean isCancellation(Throwable error) {
        if (error instanceof CancellationException) {
            return true;
        }
        Throwable cause = error instanceof CompletionException ? error.getCause() : null;
        return cause instanceof CancellationException;
    }
}
