package xin.v5ai.nb.common.core.platform;

import java.util.List;

public interface TenantStore {
    Tenant save(Tenant tenant);

    Tenant findById(Long id);

    Tenant findByKey(String tenantKey);

    List<Tenant> list();

    void disable(Long id);
}
