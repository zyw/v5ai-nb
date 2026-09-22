package xin.v5ai.nb.platform.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.platform.api.RateLimiter;
import xin.v5ai.nb.platform.api.domain.AppQuotaDTO;
import xin.v5ai.nb.platform.api.domain.AppUsageDTO;
import xin.v5ai.nb.platform.domain.V5aiAppQuota;
import xin.v5ai.nb.platform.mapper.V5aiAppQuotaMapper;
import xin.v5ai.nb.platform.mapper.V5aiAppUsageMapper;
import xin.v5ai.nb.platform.service.IV5aiAppQuotaService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 应用配额/用量服务：v5ai_app_quota（配额配置）+ v5ai_app_usage（按日用量）+ 内存限流器。
 * 0 表示不限制。
 */
@Service
@RequiredArgsConstructor
public class V5aiAppQuotaServiceImpl implements IV5aiAppQuotaService, AppQuotaService {

    private final V5aiAppQuotaMapper quotaMapper;
    private final V5aiAppUsageMapper usageMapper;
    private final RateLimiter rateLimiter;

    @Override
    public AppQuotaDTO getQuota(String agentKey) {
        var entity = quotaMapper.selectByApp(agentKey);
        return entity == null
                ? new AppQuotaDTO(agentKey, 0, 0, 0, null,null)
                : new AppQuotaDTO(entity.getAgentKey(), nz(entity.getDailyModelCalls()),
                        nz(entity.getDailyTokens()), nz(entity.getRatePerMinute()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public AppQuotaDTO updateQuota(String agentKey, Integer dailyModelCalls, Long dailyTokens, Integer ratePerMinute) {
        var existing = quotaMapper.selectByApp(agentKey);
        var entity = existing != null ? existing : new V5aiAppQuota();
        entity.setAgentKey(agentKey);
        entity.setDailyModelCalls(dailyModelCalls != null ? dailyModelCalls : nz(entity.getDailyModelCalls()));
        entity.setDailyTokens(dailyTokens != null ? dailyTokens : nz(entity.getDailyTokens()));
        entity.setRatePerMinute(ratePerMinute != null ? ratePerMinute : nz(entity.getRatePerMinute()));
        entity.setUpdatedAt(OffsetDateTime.now());
        if (existing == null) {
            quotaMapper.insert(entity);
        } else {
            quotaMapper.updateById(entity);
        }
        return new AppQuotaDTO(entity.getAgentKey(), nz(entity.getDailyModelCalls()), nz(entity.getDailyTokens()),
                nz(entity.getRatePerMinute()),entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public boolean checkAllowed(String agentKey) {
        var quota = getQuota(agentKey);
        // 每分钟限流
        if (quota.getRatePerMinute() > 0 && !rateLimiter.tryAcquire(agentKey, quota.getRatePerMinute())) {
            return false;
        }
        // 每日调用次数配额
        var usage = usageMapper.selectByAppAndDate(agentKey, LocalDate.now());
        if (quota.getDailyModelCalls() > 0 && usage != null && usage.getModelCalls() >= quota.getDailyModelCalls()) {
            return false;
        }
        return true;
    }

    @Override
    public void recordUsage(String agentKey, long tokens) {
        // 原子 upsert：按复合主键 (agent_key, usage_date) 累加，避免 updateById 误改多行/主键冲突
        usageMapper.upsertUsage(agentKey, LocalDate.now(), Math.max(tokens, 0));
    }

    @Override
    public AppUsageDTO getDailyUsage(String agentKey) {
        var entity = usageMapper.selectByAppAndDate(agentKey, LocalDate.now());
        return entity == null
                ? new AppUsageDTO(agentKey, LocalDate.now(), 0, 0)
                : new AppUsageDTO(agentKey, entity.getUsageDate(), nz(entity.getModelCalls()), nz(entity.getTokens()));
    }

    @Override
    public List<AppUsageDTO> getUsageRange(String agentKey, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        return usageMapper.selectByAppRange(agentKey, from, to).stream()
                .map(entity -> new AppUsageDTO(agentKey, entity.getUsageDate(), nz(entity.getModelCalls()), nz(entity.getTokens())))
                .toList();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
