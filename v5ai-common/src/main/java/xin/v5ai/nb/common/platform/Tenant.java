package xin.v5ai.nb.common.platform;

import java.time.Instant;

/**
 * 租户领域模型。
 *
 * @param id         主键
 * @param tenantKey  租户唯一标识
 * @param name       租户名称
 * @param status     ACTIVE / DISABLED
 * @param createdAt  创建时间
 * @param updatedAt  更新时间
 */
public record Tenant(
        Long id,
        String tenantKey,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
