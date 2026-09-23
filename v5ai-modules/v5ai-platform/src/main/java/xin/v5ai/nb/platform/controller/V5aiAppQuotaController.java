package xin.v5ai.nb.platform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.platform.api.domain.AppQuotaDTO;
import xin.v5ai.nb.platform.api.domain.AppUsageDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 应用配额与当日用量 API（限流/配额/用量管理）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/apps/{agentKey}")
public class V5aiAppQuotaController {

    private final AppQuotaService quotaService;

    public record UpdateQuotaRequest(Integer dailyModelCalls, Long dailyTokens, Integer ratePerMinute) {
    }

    @GetMapping("/quota")
    public R<AppQuotaDTO> getQuota(@PathVariable("agentKey") String agentKey) {
        return R.ok(quotaService.getQuota(agentKey));
    }

    @PutMapping("/quota")
    @Log(title = "更新应用配额", businessType = BusinessType.UPDATE)
    public R<AppQuotaDTO> updateQuota(@PathVariable("agentKey") String agentKey, @RequestBody UpdateQuotaRequest request) {
        var quota = quotaService.updateQuota(agentKey, request.dailyModelCalls(), request.dailyTokens(),
                request.ratePerMinute());
        return R.ok(quota);
    }

    @GetMapping("/usage/daily")
    public R<AppUsageDTO> getDailyUsage(@PathVariable("agentKey") String agentKey) {
        return R.ok(quotaService.getDailyUsage(agentKey));
    }

    @GetMapping("/usage/range")
    public R<List<AppUsageDTO>> getUsageRange(@PathVariable("agentKey") String agentKey,
                                              @RequestParam LocalDate from,
                                              @RequestParam LocalDate to) {
        return R.ok(quotaService.getUsageRange(agentKey, from, to));
    }
}
