package xin.v5ai.nb.platform.controller;

import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.page.PageUtils;
import xin.v5ai.nb.common.core.platform.AuditLogRecorderService;
import xin.v5ai.nb.common.core.platform.Tenant;
import xin.v5ai.nb.common.core.platform.TenantStore;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;

import java.time.Instant;

/**
 * 租户管理 API。
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {
    private final TenantStore tenantStore;
    private final AuditLogRecorderService auditRecorder;

    public TenantController(TenantStore tenantStore, AuditLogRecorderService auditRecorder) {
        this.tenantStore = tenantStore;
        this.auditRecorder = auditRecorder;
    }

    public record CreateTenantRequest(String tenantKey, String name) {
    }

    @PostMapping
    public R<Tenant> createTenant(@RequestBody CreateTenantRequest request) {
        if (request.tenantKey() == null || request.tenantKey().isBlank()
                || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("tenant key and name are required");
        }
        if (tenantStore.findByKey(request.tenantKey().trim()) != null) {
            throw new IllegalArgumentException("tenant key already exists: " + request.tenantKey());
        }
        var tenant = tenantStore.save(new Tenant(null, request.tenantKey().trim(), request.name().trim(),
                "ACTIVE", Instant.now(), Instant.now()));
        auditRecorder.record(tenant.id(), currentActor(), "CREATE", "tenant",
                String.valueOf(tenant.id()), "tenant " + tenant.tenantKey(), null);
        return R.ok(tenant);
    }

    @GetMapping
    public R<PageResult<Tenant>> listTenants(PageQuery pageQuery) {
        return R.ok(PageUtils.page(tenantStore.list(), pageQuery));
    }

    @DeleteMapping("/{id}")
    public R<Void> disableTenant(@PathVariable("id") Long id) {
        tenantStore.disable(id);
        auditRecorder.record(id, currentActor(), "DISABLE", "tenant",
                String.valueOf(id), "disable tenant", null);
        return R.ok();
    }

    private static String currentActor() {
        try {
            return LoginHelper.getUsername();
        } catch (Exception exception) {
            return null;
        }
    }
}
