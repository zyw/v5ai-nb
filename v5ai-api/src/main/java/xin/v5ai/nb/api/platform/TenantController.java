package xin.v5ai.nb.api.platform;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.core.platform.Tenant;
import xin.v5ai.nb.common.core.platform.TenantStore;

import java.time.Instant;
import java.util.List;

/**
 * 租户管理 API。
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {
    private final TenantStore tenantStore;
    private final AuditRecorder auditRecorder;

    public TenantController(TenantStore tenantStore, AuditRecorder auditRecorder) {
        this.tenantStore = tenantStore;
        this.auditRecorder = auditRecorder;
    }

    public record CreateTenantRequest(String tenantKey, String name) {
    }

    @PostMapping
    public Tenant createTenant(@RequestBody CreateTenantRequest request) {
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
        return tenant;
    }

    @GetMapping
    public List<Tenant> listTenants() {
        return tenantStore.list();
    }

    @DeleteMapping("/{id}")
    public void disableTenant(@PathVariable("id") Long id) {
        tenantStore.disable(id);
        auditRecorder.record(id, currentActor(), "DISABLE", "tenant",
                String.valueOf(id), "disable tenant", null);
    }

    private static String currentActor() {
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            return null;
        }
    }
}
