package xin.v5ai.nb.api.auth;

import xin.v5ai.nb.common.platform.RoleKey;

import java.util.List;

/**
 * 登录响应：令牌 + 当前用户信息（用户/租户/角色，供前端与 RBAC 使用）。
 */
public record LoginResponse(
        String tokenType,
        String token,
        Long userId,
        String username,
        String displayName,
        Long tenantId,
        List<RoleKey> roles
) {
    /** 兼容旧字段：仅令牌。 */
    public static LoginResponse of(String tokenType, String token) {
        return new LoginResponse(tokenType, token, null, null, null, null, List.of());
    }
}
