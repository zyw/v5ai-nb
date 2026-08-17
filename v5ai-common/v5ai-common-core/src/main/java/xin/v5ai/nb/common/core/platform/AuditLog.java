package xin.v5ai.nb.common.core.platform;

import java.time.Instant;

/**
 * 审计日志条目。
 *
 * @param id         主键
 * @param tenantId   租户（可为 null）
 * @param actor      操作者（用户名）
 * @param action     动作（LOGIN / CREATE / DELETE / PUBLISH / BIND / ...）
 * @param targetType 目标类型（application / model / tenant / api-key / ...）
 * @param targetId   目标 ID
 * @param detail     详情
 * @param ip         来源 IP
 * @param createdAt  时间
 */
public record AuditLog(
        Long id,
        Long tenantId,
        String actor,
        String action,
        String targetType,
        String targetId,
        String detail,
        String ip,
        Instant createdAt
) {
}
