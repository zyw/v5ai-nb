package xin.v5ai.nb.infrastructure.platform;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.platform.AppQuota;
import xin.v5ai.nb.common.platform.AppQuotaService;
import xin.v5ai.nb.common.platform.AppUsage;
import xin.v5ai.nb.common.platform.RateLimiter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 应用配额/用量服务：v5ai_app_quota（配额配置）+ v5ai_app_usage（按日用量）+ 内存限流器。
 * 0 表示不限制。
 */
@Service
public class MyBatisAppQuotaService implements AppQuotaService {
    private final AppQuotaMapper quotaMapper;
    private final AppUsageMapper usageMapper;
    private final RateLimiter rateLimiter;

    public MyBatisAppQuotaService(AppQuotaMapper quotaMapper, AppUsageMapper usageMapper, RateLimiter rateLimiter) {
        this.quotaMapper = quotaMapper;
        this.usageMapper = usageMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public AppQuota getQuota(String agentKey) {
        var entity = quotaMapper.selectByApp(agentKey);
        return entity == null
                ? new AppQuota(agentKey, 0, 0, 0, null)
                : new AppQuota(entity.getAgentKey(), nz(entity.getDailyModelCalls()),
                        nz(entity.getDailyTokens()), nz(entity.getRatePerMinute()), entity.getUpdatedAt());
    }

    @Override
    public AppQuota updateQuota(String agentKey, Integer dailyModelCalls, Long dailyTokens, Integer ratePerMinute) {
        var existing = quotaMapper.selectByApp(agentKey);
        var entity = existing != null ? existing : new AppQuotaEntity();
        entity.setAgentKey(agentKey);
        entity.setDailyModelCalls(dailyModelCalls != null ? dailyModelCalls : nz(entity.getDailyModelCalls()));
        entity.setDailyTokens(dailyTokens != null ? dailyTokens : nz(entity.getDailyTokens()));
        entity.setRatePerMinute(ratePerMinute != null ? ratePerMinute : nz(entity.getRatePerMinute()));
        entity.setUpdatedAt(Instant.now());
        if (existing == null) {
            quotaMapper.insert(entity);
        } else {
            quotaMapper.updateById(entity);
        }
        return new AppQuota(entity.getAgentKey(), nz(entity.getDailyModelCalls()), nz(entity.getDailyTokens()),
                nz(entity.getRatePerMinute()), entity.getUpdatedAt());
    }

    @Override
    public boolean checkAllowed(String agentKey) {
        var quota = getQuota(agentKey);
        // 每分钟限流
        if (quota.ratePerMinute() > 0 && !rateLimiter.tryAcquire(agentKey, quota.ratePerMinute())) {
            return false;
        }
        // 每日调用次数配额
        var usage = usageMapper.selectByAppAndDate(agentKey, LocalDate.now());
        if (quota.dailyModelCalls() > 0 && usage != null && usage.getModelCalls() >= quota.dailyModelCalls()) {
            return false;
        }
        return true;
    }

    @Override
    public void recordUsage(String agentKey, long tokens) {
        var today = LocalDate.now();
        var entity = usageMapper.selectByAppAndDate(agentKey, today);
        if (entity == null) {
            entity = new AppUsageEntity();
            entity.setAgentKey(agentKey);
            entity.setUsageDate(today);
            entity.setModelCalls(1);
            entity.setTokens(Math.max(tokens, 0));
            entity.setUpdatedAt(Instant.now());
            usageMapper.insert(entity);
            return;
        }
        entity.setModelCalls(entity.getModelCalls() + 1);
        entity.setTokens(entity.getTokens() + Math.max(tokens, 0));
        entity.setUpdatedAt(Instant.now());
        usageMapper.updateById(entity);
    }

    @Override
    public AppUsage getDailyUsage(String agentKey) {
        var entity = usageMapper.selectByAppAndDate(agentKey, LocalDate.now());
        return entity == null
                ? new AppUsage(agentKey, LocalDate.now(), 0, 0)
                : new AppUsage(agentKey, entity.getUsageDate(), nz(entity.getModelCalls()), nz(entity.getTokens()));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
