package xin.v5ai.nb.api.platform;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.core.platform.UserAccount;
import xin.v5ai.nb.common.core.platform.UserAccountStore;

import java.util.List;

/**
 * 用户管理 API（RBAC）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {
    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    public UserController(UserAccountStore userAccountStore, PasswordEncoder passwordEncoder,
                          AuditRecorder auditRecorder) {
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
    }

    public record CreateUserRequest(String username, String password, String displayName,
                                    Long tenantId, List<String> roles) {
    }

    public record UpdateRolesRequest(List<String> roles) {
    }

    public record UpdateEnabledRequest(boolean enabled) {
    }

    @GetMapping
    public List<UserAccount> listUsers() {
        return userAccountStore.list();
    }

    @PostMapping
    public UserAccount createUser(@RequestBody CreateUserRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().length() < 4) {
            throw new IllegalArgumentException("username and a password of at least 4 characters are required");
        }
        if (userAccountStore.findByUsername(request.username().trim()) != null) {
            throw new IllegalArgumentException("username already exists: " + request.username());
        }
        var roles = request.roles() == null || request.roles().isEmpty()
                ? List.of("USER") : request.roles();
        var account = userAccountStore.create(request.username().trim(),
                passwordEncoder.encode(request.password()), request.displayName(), request.tenantId(), roles);
        auditRecorder.record(request.tenantId(), currentActor(), "CREATE", "user",
                String.valueOf(account.id()), "user " + account.username(), null);
        return account;
    }

    @PutMapping("/{id}/roles")
    public UserAccount updateRoles(@PathVariable("id") Long id, @RequestBody UpdateRolesRequest request) {
        if (request.roles() == null || request.roles().isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        userAccountStore.updateRoles(id, request.roles());
        auditRecorder.record(null, currentActor(), "UPDATE_ROLES", "user",
                String.valueOf(id), "roles=" + request.roles(), null);
        return userAccountStore.findById(id);
    }

    @PutMapping("/{id}/enabled")
    public UserAccount updateEnabled(@PathVariable("id") Long id, @RequestBody UpdateEnabledRequest request) {
        userAccountStore.setEnabled(id, request.enabled());
        auditRecorder.record(null, currentActor(), "UPDATE_ENABLED", "user",
                String.valueOf(id), "enabled=" + request.enabled(), null);
        return userAccountStore.findById(id);
    }

    private static String currentActor() {
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            return null;
        }
    }
}
