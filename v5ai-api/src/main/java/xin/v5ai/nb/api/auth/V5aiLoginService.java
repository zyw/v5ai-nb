package xin.v5ai.nb.api.auth;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.auth.UserCredentialStore;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.core.platform.Menu;
import xin.v5ai.nb.common.core.platform.MenuStore;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.common.core.service.PermissionService;
import xin.v5ai.nb.infrastructure.security.PasswordVerifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class V5aiLoginService implements PermissionService {
    private final UserCredentialStore credentialStore;
    private final UserAccountStore userAccountStore;
    private final PasswordVerifier passwordVerifier;
    private final LoginTokenIssuer tokenIssuer;
    private final AuditRecorder auditRecorder;
    private final MenuStore menuStore;

    public V5aiLoginService(UserCredentialStore credentialStore,
                            UserAccountStore userAccountStore,
                            PasswordVerifier passwordVerifier,
                            LoginTokenIssuer tokenIssuer,
                            AuditRecorder auditRecorder,
                            MenuStore menuStore) {
        this.credentialStore = credentialStore;
        this.userAccountStore = userAccountStore;
        this.passwordVerifier = passwordVerifier;
        this.tokenIssuer = tokenIssuer;
        this.auditRecorder = auditRecorder;
        this.menuStore = menuStore;
    }

    public LoginResponse login(LoginRequest request) {
        var account = userAccountStore.findByUsername(request.username());
        var passwordHash = credentialStore.findPasswordHash(request.username());
        if (account == null || passwordHash == null || !passwordVerifier.matches(request.password(), passwordHash)) {
            auditRecorder.record(account == null ? null : account.tenantId(),
                    request.username(), "LOGIN_FAILED", "auth", request.username(),
                    "invalid credentials", null);
            throw new IllegalArgumentException("invalid credentials");
        }
        if (!account.enabled()) {
            throw new IllegalArgumentException("account is disabled");
        }
        var roles = account.roles() == null ? List.<String>of() : account.roles();
        var token = tokenIssuer.issue(request.username());
        // 角色写入 Sa-Token session，供管理 API RBAC 拦截使用
        try {
            StpUtil.getSession().set("roles", roles);
        } catch (Exception exception) {
            // 无 Sa-Token 上下文（如测试直调）时忽略，生产登录必有上下文
        }
        auditRecorder.record(account.tenantId(), account.username(), "LOGIN",
                "auth", request.username(), "login success", null);
        return new LoginResponse("Bearer", token, account.id(), account.username(),
                account.displayName(), account.tenantId(), roles);
    }

    public void logout() {
        var username = StpUtil.getLoginIdDefaultNull() == null ? null : StpUtil.getLoginIdAsString();
        tokenIssuer.logout();
        if (username != null) {
            auditRecorder.record(null, username, "LOGOUT", "auth", username, "logout", null);
        }
    }

    @Override
    public Set<String> getRolePermission(Long userId) {
        var account = userAccountStore.findById(userId);
        if (account == null || account.roles() == null) {
            return Set.of();
        }
        return new HashSet<>(account.roles());
    }

    @Override
    public Set<String> getMenuPermission(Long userId) {
        var account = userAccountStore.findById(userId);
        if (account == null || account.roles() == null || account.roles().isEmpty()) {
            return Set.of();
        }
        return menuStore.listByRoleKeys(account.roles()).stream()
                .map(Menu::code)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }
}
