package xin.v5ai.nb.common.platform;

import java.util.List;

/**
 * 用户账号（含角色与租户归属）。
 *
 * @param id          主键
 * @param username    登录名
 * @param displayName 显示名（可为 null）
 * @param tenantId    归属租户（可为 null，null 表示默认租户）
 * @param roles       角色集合
 * @param enabled     是否启用
 */
public record UserAccount(
        Long id,
        String username,
        String displayName,
        Long tenantId,
        List<String> roles,
        boolean enabled
) {
    public boolean hasRole(RoleKey role) {
        return roles != null && roles.contains(role.name());
    }
}
