package xin.v5ai.nb.platform.service.impl;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.core.platform.Tenant;
import xin.v5ai.nb.common.core.platform.TenantStore;
import xin.v5ai.nb.platform.mapper.TenantMapper;

import java.util.List;

@Repository
public class MyBatisTenantStore implements TenantStore {
    private final TenantMapper mapper;

    public MyBatisTenantStore(TenantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Tenant save(Tenant tenant) {
        var entity = new xin.v5ai.nb.platform.domain.Tenant();
        entity.setTenantKey(tenant.tenantKey());
        entity.setName(tenant.name());
        entity.setStatus(tenant.status());
        entity.setCreatedAt(tenant.createdAt());
        entity.setUpdatedAt(tenant.updatedAt());
        mapper.insert(entity);
        return new Tenant(entity.getId(), entity.getTenantKey(), entity.getName(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public Tenant findById(Long id) {
        return toDomain(mapper.selectById(id));
    }

    @Override
    public Tenant findByKey(String tenantKey) {
        return toDomain(mapper.selectByKey(tenantKey));
    }

    @Override
    public List<Tenant> list() {
        return mapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void disable(Long id) {
        mapper.disableTenant(id);
    }

    private Tenant toDomain(xin.v5ai.nb.platform.domain.Tenant entity) {
        if (entity == null) {
            return null;
        }
        return new Tenant(entity.getId(), entity.getTenantKey(), entity.getName(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
