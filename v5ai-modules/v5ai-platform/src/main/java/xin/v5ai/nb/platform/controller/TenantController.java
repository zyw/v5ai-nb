package xin.v5ai.nb.platform.controller;

import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.platform.Tenant;
import xin.v5ai.nb.common.core.platform.TenantStore;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;

import java.time.Instant;

/**
 * 租户管理 API。
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {
    private final TenantStore tenantStore;

    public TenantController(TenantStore tenantStore) {
        this.tenantStore = tenantStore;
    }

    public record CreateTenantRequest(String tenantKey, String name) {
    }

    @PostMapping
    @Log(title = "新建租户", businessType = BusinessType.INSERT)
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
        return R.ok(tenant);
    }

    @GetMapping
    public R<PageResult<Tenant>> listTenants(PageQuery pageQuery) {
//       TODO PageUtils return R.ok(PageUtils.page(tenantStore.list(), pageQuery));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Log(title = "禁用租户", businessType = BusinessType.DISABLE)
    public R<Void> disableTenant(@PathVariable("id") Long id) {
        tenantStore.disable(id);
        return R.ok();
    }
}
