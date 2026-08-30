//package xin.v5ai.nb.platform.controller.auth;
//
//import java.util.List;
//
///**
// * 登录响应：令牌 + 当前用户信息（用户/角色，供前端与 RBAC 使用）。
// */
//public record LoginResponse(
//        String tokenType,
//        String token,
//        Long userId,
//        String username,
//        String displayName,
//        List<String> roles
//) {
//    /** 兼容旧字段：仅令牌。 */
//    public static LoginResponse of(String tokenType, String token) {
//        return new LoginResponse(tokenType, token, null, null, null, List.of());
//    }
//}
