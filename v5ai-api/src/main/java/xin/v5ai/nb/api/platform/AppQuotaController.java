package xin.v5ai.nb.api.platform;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.platform.AppQuota;
import xin.v5ai.nb.common.platform.AppQuotaService;
import xin.v5ai.nb.common.platform.AppUsage;
import xin.v5ai.nb.common.platform.AuditRecorder;

/**
 * 应用配额与当日用量 API（限流/配额/用量管理）。
 */
@RestController
@RequestMapping("/api/admin/apps/{appKey}")
public class AppQuotaController {
    private final AppQuotaService quotaService;
    private final AuditRecorder auditRecorder;

    public AppQuotaController(AppQuotaService quotaService, AuditRecorder auditRecorder) {
        this.quotaService = quotaService;
        this.auditRecorder = auditRecorder;
    }

    public record UpdateQuotaRequest(Integer dailyModelCalls, Long dailyTokens, Integer ratePerMinute) {
    }

    @GetMapping("/quota")
    public AppQuota getQuota(@PathVariable("appKey") String appKey) {
        return quotaService.getQuota(appKey);
    }

    @PutMapping("/quota")
    public AppQuota updateQuota(@PathVariable("appKey") String appKey, @RequestBody UpdateQuotaRequest request) {
        var quota = quotaService.updateQuota(appKey, request.dailyModelCalls(), request.dailyTokens(),
                request.ratePerMinute());
        auditRecorder.record(null, currentActor(), "UPDATE_QUOTA", "application",
                appKey, "quota=" + quota, null);
        return quota;
    }

    @GetMapping("/usage/daily")
    public AppUsage getDailyUsage(@PathVariable("appKey") String appKey) {
        return quotaService.getDailyUsage(appKey);
    }

    private static String currentActor() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            return null;
        }
    }
}
