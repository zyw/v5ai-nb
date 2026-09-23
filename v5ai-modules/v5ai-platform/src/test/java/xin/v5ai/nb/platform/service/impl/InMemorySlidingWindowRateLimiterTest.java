package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySlidingWindowRateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        var limiter = new InMemorySlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("app-1", 2)).isTrue();
        assertThat(limiter.tryAcquire("app-1", 2)).isTrue();
    }

    @Test
    void rejectsWhenLimitExceeded() {
        var limiter = new InMemorySlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("app-1", 2)).isTrue();
        assertThat(limiter.tryAcquire("app-1", 2)).isTrue();
        assertThat(limiter.tryAcquire("app-1", 2)).isFalse();
    }

    @Test
    void keysAreIsolated() {
        var limiter = new InMemorySlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("app-1", 1)).isTrue();
        assertThat(limiter.tryAcquire("app-1", 1)).isFalse();
        assertThat(limiter.tryAcquire("app-2", 1)).isTrue();
    }

    @Test
    void zeroLimitMeansUnlimited() {
        var limiter = new InMemorySlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("app-1", 0)).isTrue();
        assertThat(limiter.tryAcquire("app-1", 0)).isTrue();
    }
}
