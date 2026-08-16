package xin.v5ai.nb.common.platform;

import java.util.List;

/**
 * 审计记录器：记录管理面操作。实现允许 no-op（如测试环境）。
 */
public interface AuditRecorder {

    /**
     * 记录一条审计。
     *
     * @param tenantId   租户（可为 null）
     * @param actor      操作者（可为 null）
     * @param action     动作
     * @param targetType 目标类型（可为 null）
     * @param targetId   目标 ID（可为 null）
     * @param detail     详情（可为 null）
     * @param ip         来源 IP（可为 null）
     */
    void record(Long tenantId, String actor, String action,
                String targetType, String targetId, String detail, String ip);

    List<AuditLog> search(String actor, String action, int limit);
}
