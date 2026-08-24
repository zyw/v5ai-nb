package xin.v5ai.nb.platform.auth;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.auth.UserCredentialStore;
import xin.v5ai.nb.common.core.domain.LoginUser;
import xin.v5ai.nb.common.core.domain.RoleDTO;
import xin.v5ai.nb.common.core.platform.*;
import xin.v5ai.nb.common.core.service.PermissionService;
import xin.v5ai.nb.common.core.utils.ServletUtils;
import xin.v5ai.nb.common.core.utils.ThreadUtils;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.security.PasswordVerifier;
import xin.v5ai.nb.platform.service.IRoleService;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class V5aiLoginService {
    private final UserCredentialStore credentialStore;
    private final UserAccountStore userAccountStore;
    private final PasswordVerifier passwordVerifier;
    private final AuditLogRecorderService auditRecorder;
    private final PermissionService permissionService;
    private final IRoleService roleService;
    private final SaTokenConfig saTokenConfig;

    public LoginResponse login(LoginRequest request) {
        var account = userAccountStore.findByUsername(request.username());
        var passwordHash = credentialStore.findPasswordHash(request.username());
        if (account == null || passwordHash == null || !passwordVerifier.matches(request.password(), passwordHash)) {
            auditRecorder.record(account == null ? null : account.tenantId(),
                    request.username(), "LOGIN_FAILED", "auth", request.username(),
                    "invalid credentials", null);
            log.warn("invalid credentials for {}", request.username());
            throw new IllegalArgumentException("invalid credentials");
        }
        if (!account.isEnabled()) {
            log.warn("account {} is disabled", account.username());
            throw new IllegalArgumentException("account is disabled");
        }

        // 此处可根据登录用户的数据不同 自行创建 loginUser
        LoginUser loginUser = buildLoginUser(account);
        SaLoginParameter model = new SaLoginParameter();
        model.setTimeout(saTokenConfig.getTimeout());
        model.setActiveTimeout(saTokenConfig.getActiveTimeout());
        // 生成token
        LoginHelper.login(loginUser, model);
        var roles = account.roles() == null ? List.<String>of() : account.roles();

        auditRecorder.record(account.tenantId(), account.username(), "LOGIN",
                "auth", request.username(), "login success", null);
        // 登录成功：回写最后登录 IP 与时间（失败不阻断登录）
        recordLoginInfo(account.id());
        return new LoginResponse("Bearer", StpUtil.getTokenValue(), account.id(), account.username(),
                account.displayName(), account.tenantId(), roles);
    }

    /**
     * 登录成功后回写最后登录 IP 与时间。无请求上下文（如测试直调）时 IP 记为空串；
     * 回写失败仅告警，不影响登录流程。
     */
    private void recordLoginInfo(Long userId) {
        try {
            String loginIp;
            try {
                loginIp = ServletUtils.getClientIP();
            } catch (Exception ignored) {
                loginIp = "";
            }
            userAccountStore.updateLoginInfo(userId, loginIp, Instant.now());
        } catch (Exception exception) {
            log.warn("failed to record login info for user {}: {}", userId, exception.getMessage());
        }
    }

    public void logout() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            if (ObjectUtil.isNull(loginUser)) {
                return;
            }
            auditRecorder.record(null, loginUser.getUsername(), "LOGOUT", "auth", loginUser.getUsername() , "logout", null);
        } catch (NotLoginException ignored) {
        } finally {
            try {
                StpUtil.logout();
            } catch (NotLoginException ignored) {
            }
        }
    }

    /**
     * 根据用户视图对象组装登录态上下文。
     *
     * @param user 用户基础信息
     * @return 包含部门、角色、岗位与权限数据的登录用户
     */
    public LoginUser buildLoginUser(UserAccount user) {
        LoginUser loginUser = new LoginUser();
        Long userId = user.id();
        loginUser.setUserId(userId);
        loginUser.setUsername(user.username());
        loginUser.setNickname(user.displayName());
        ThreadUtils.virtualInvokeAll(() -> {
            loginUser.setMenuPermission(permissionService.getMenuPermission(userId));
        }, () -> {
            loginUser.setRolePermission(permissionService.getRolePermission(userId));
        }, () -> {
            List<Role> roles = roleService.selectRolesByUserId(userId);
            List<RoleDTO> roleDtos = BeanUtil.copyToList(roles, RoleDTO.class);
            loginUser.setRoles(roleDtos);
            loginUser.setDataScopeRoleMap(permissionService.getDataScopeRoleMap(roleDtos));
        });
        return loginUser;
    }
}
