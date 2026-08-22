package xin.v5ai.nb.platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditLog;
import xin.v5ai.nb.common.core.platform.AuditRecorder;

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
    public R<PageResult<AuditLog>> search(
            PageQuery pageQuery,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) String action
    ) {
        int limit = pageQuery.getPageSize() == null ? 100 : pageQuery.getPageSize();
        return R.ok(PageUtils.page(auditRecorder.search(actor, action, limit), pageQuery));
    }
}
