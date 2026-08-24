package xin.v5ai.nb.platform.controller;

import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.platform.AppQuota;
import xin.v5ai.nb.common.core.platform.AppQuotaService;
import xin.v5ai.nb.common.core.platform.AppUsage;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;

/**
 * 应用配额与当日用量 API（限流/配额/用量管理）。
 */
@RestController
@RequestMapping("/api/admin/apps/{agentKey}")
public class AppQuotaController {
    private final AppQuotaService quotaService;

    public AppQuotaController(AppQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    public record UpdateQuotaRequest(Integer dailyModelCalls, Long dailyTokens, Integer ratePerMinute) {
    }

    @GetMapping("/quota")
    public R<AppQuota> getQuota(@PathVariable("agentKey") String agentKey) {
        return R.ok(quotaService.getQuota(agentKey));
    }

    @PutMapping("/quota")
    @Log(title = "更新应用配额", businessType = BusinessType.UPDATE)
    public R<AppQuota> updateQuota(@PathVariable("agentKey") String agentKey, @RequestBody UpdateQuotaRequest request) {
        var quota = quotaService.updateQuota(agentKey, request.dailyModelCalls(), request.dailyTokens(),
                request.ratePerMinute());
        return R.ok(quota);
    }

    @GetMapping("/usage/daily")
    public R<AppUsage> getDailyUsage(@PathVariable("agentKey") String agentKey) {
        return R.ok(quotaService.getDailyUsage(agentKey));
    }
}
