package xin.v5ai.nb.api.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.platform.AuditLog;
import xin.v5ai.nb.common.platform.AuditRecorder;

import java.util.List;

/**
 * 审计日志查询 API。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditController {
    private final AuditRecorder auditRecorder;

    public AuditController(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @GetMapping
    public List<AuditLog> search(
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return auditRecorder.search(actor, action, limit);
    }
}
