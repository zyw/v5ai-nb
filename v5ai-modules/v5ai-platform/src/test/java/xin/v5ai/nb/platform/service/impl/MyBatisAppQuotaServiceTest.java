package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.platform.api.RateLimiter;
import xin.v5ai.nb.platform.domain.V5aiAppQuota;
import xin.v5ai.nb.platform.domain.V5aiAppUsage;
import xin.v5ai.nb.platform.mapper.V5aiAppQuotaMapper;
import xin.v5ai.nb.platform.mapper.V5aiAppUsageMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MyBatisAppQuotaServiceTest {

    @Test
    void unlimitedByDefault() {
        var quotaMapper = mock(V5aiAppQuotaMapper.class);
        var service = new V5aiAppQuotaServiceImpl(quotaMapper, mock(V5aiAppUsageMapper.class), rateLimiter(true));
        var quota = service.getQuota("demo");
        assertThat(quota.getDailyModelCalls()).isZero();
        assertThat(quota.getRatePerMinute()).isZero();
        assertThat(service.checkAllowed("demo")).isTrue();
    }

    @Test
    void updateQuotaReturnsConfiguredValues() {
        var quotaMapper = mock(V5aiAppQuotaMapper.class);
        var service = new V5aiAppQuotaServiceImpl(quotaMapper, mock(V5aiAppUsageMapper.class), rateLimiter(true));
        var quota = service.updateQuota("demo", 100, 50_000L, 30);
        assertThat(quota.getDailyModelCalls()).isEqualTo(100);
        assertThat(quota.getDailyTokens()).isEqualTo(50_000L);
        assertThat(quota.getRatePerMinute()).isEqualTo(30);
    }

    @Test
    void dailyCallsQuotaBlocksWhenExceeded() {
        var quotaMapper = mock(V5aiAppQuotaMapper.class);
        var usageMapper = mock(V5aiAppUsageMapper.class);
        var quotaEntity = new V5aiAppQuota();
        quotaEntity.setAgentKey("demo");
        quotaEntity.setDailyModelCalls(5);
        quotaEntity.setDailyTokens(0L);
        quotaEntity.setRatePerMinute(0);
        when(quotaMapper.selectByApp("demo")).thenReturn(quotaEntity);
        var usageEntity = new V5aiAppUsage();
        usageEntity.setAgentKey("demo");
        usageEntity.setUsageDate(LocalDate.now());
        usageEntity.setModelCalls(5);
        when(usageMapper.selectByAppAndDate("demo", LocalDate.now())).thenReturn(usageEntity);

        var service = new V5aiAppQuotaServiceImpl(quotaMapper, usageMapper, rateLimiter(true));
        assertThat(service.checkAllowed("demo")).isFalse();
    }

    @Test
    void rateLimitBlocksWhenExceeded() {
        var quotaMapper = mock(V5aiAppQuotaMapper.class);
        var quotaEntity = new V5aiAppQuota();
        quotaEntity.setAgentKey("demo");
        quotaEntity.setRatePerMinute(10);
        when(quotaMapper.selectByApp("demo")).thenReturn(quotaEntity);

        var service = new V5aiAppQuotaServiceImpl(quotaMapper, mock(V5aiAppUsageMapper.class), rateLimiter(false));
        assertThat(service.checkAllowed("demo")).isFalse();
    }

    @Test
    void recordUsageInsertsNewDailyRow() {
        var quotaMapper = mock(V5aiAppQuotaMapper.class);
        var usageMapper = mock(V5aiAppUsageMapper.class);
        var service = new V5aiAppQuotaServiceImpl(quotaMapper, usageMapper, rateLimiter(true));

        service.recordUsage("demo", 123);

        var captor = ArgumentCaptor.forClass(V5aiAppUsage.class);
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
