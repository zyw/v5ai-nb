package xin.v5ai.nb.infrastructure.platform;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.platform.RateLimiter;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisAppQuotaServiceTest {

    @Test
    void unlimitedByDefault() {
        var quotaMapper = mock(AppQuotaMapper.class);
        var service = new MyBatisAppQuotaService(quotaMapper, mock(AppUsageMapper.class), rateLimiter(true));
        var quota = service.getQuota("demo");
        assertThat(quota.dailyModelCalls()).isZero();
        assertThat(quota.ratePerMinute()).isZero();
        assertThat(service.checkAllowed("demo")).isTrue();
    }

    @Test
    void updateQuotaReturnsConfiguredValues() {
        var quotaMapper = mock(AppQuotaMapper.class);
        var service = new MyBatisAppQuotaService(quotaMapper, mock(AppUsageMapper.class), rateLimiter(true));
        var quota = service.updateQuota("demo", 100, 50_000L, 30);
        assertThat(quota.dailyModelCalls()).isEqualTo(100);
        assertThat(quota.dailyTokens()).isEqualTo(50_000L);
        assertThat(quota.ratePerMinute()).isEqualTo(30);
    }

    @Test
    void dailyCallsQuotaBlocksWhenExceeded() {
        var quotaMapper = mock(AppQuotaMapper.class);
        var usageMapper = mock(AppUsageMapper.class);
        var quotaEntity = new AppQuotaEntity();
        quotaEntity.setAgentKey("demo");
        quotaEntity.setDailyModelCalls(5);
        quotaEntity.setDailyTokens(0L);
        quotaEntity.setRatePerMinute(0);
        when(quotaMapper.selectByApp("demo")).thenReturn(quotaEntity);
        var usageEntity = new AppUsageEntity();
        usageEntity.setAgentKey("demo");
        usageEntity.setUsageDate(LocalDate.now());
        usageEntity.setModelCalls(5);
        when(usageMapper.selectByAppAndDate("demo", LocalDate.now())).thenReturn(usageEntity);

        var service = new MyBatisAppQuotaService(quotaMapper, usageMapper, rateLimiter(true));
        assertThat(service.checkAllowed("demo")).isFalse();
    }

    @Test
    void rateLimitBlocksWhenExceeded() {
        var quotaMapper = mock(AppQuotaMapper.class);
        var quotaEntity = new AppQuotaEntity();
        quotaEntity.setAgentKey("demo");
        quotaEntity.setRatePerMinute(10);
        when(quotaMapper.selectByApp("demo")).thenReturn(quotaEntity);

        var service = new MyBatisAppQuotaService(quotaMapper, mock(AppUsageMapper.class), rateLimiter(false));
        assertThat(service.checkAllowed("demo")).isFalse();
    }

    @Test
    void recordUsageInsertsNewDailyRow() {
        var quotaMapper = mock(AppQuotaMapper.class);
        var usageMapper = mock(AppUsageMapper.class);
        var service = new MyBatisAppQuotaService(quotaMapper, usageMapper, rateLimiter(true));

        service.recordUsage("demo", 123);

        var captor = ArgumentCaptor.forClass(AppUsageEntity.class);
        verify(usageMapper).insert(captor.capture());
        assertThat(captor.getValue().getAgentKey()).isEqualTo("demo");
        assertThat(captor.getValue().getModelCalls()).isEqualTo(1);
        assertThat(captor.getValue().getTokens()).isEqualTo(123);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    private static RateLimiter rateLimiter(boolean allowed) {
        var limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire(anyString(), eq(0))).thenReturn(true);
        when(limiter.tryAcquire(anyString(), eq(10))).thenReturn(allowed);
        when(limiter.tryAcquire(anyString(), eq(30))).thenReturn(allowed);
        return limiter;
    }
}
