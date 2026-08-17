package xin.v5ai.nb.common.core.platform;

import java.time.Instant;

/**
 * 角色领域模型。
 *
 * @param roleKey     角色唯一标识（大写，如 ADMIN / USER / VIEWER）
 * @param name        角色显示名
 * @param description 角色描述（可为 null）
 * @param builtin     是否内置角色（内置角色不可删除）
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record Role(
        String roleKey,
        String name,
        String description,
        boolean builtin,
        Instant createdAt,
        Instant updatedAt
) {
}
