package xin.v5ai.nb.api.platform;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.platform.AuditRecorder;
import xin.v5ai.nb.common.platform.Role;
import xin.v5ai.nb.common.platform.RoleStore;

import java.util.List;

/**
 * 角色管理 API（角色定义 CRUD）。
 * 内置角色（ADMIN / USER）不可删除；仍被用户占用的角色不可删除。
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {
    private final RoleStore roleStore;
    private final AuditRecorder auditRecorder;

    public RoleController(RoleStore roleStore, AuditRecorder auditRecorder) {
        this.roleStore = roleStore;
        this.auditRecorder = auditRecorder;
    }

    public record CreateRoleRequest(String roleKey, String name, String description) {
    }

    public record UpdateRoleRequest(String name, String description) {
    }

    @GetMapping
    public List<Role> list() {
        return roleStore.list();
    }

    @PostMapping
    public Role create(@RequestBody CreateRoleRequest request) {
        var key = normalizeKey(request.roleKey());
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("roleKey is required");
        }
        if (!key.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("roleKey must be uppercase letters, digits or underscore");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (roleStore.findByKey(key) != null) {
            throw new IllegalArgumentException("role already exists: " + key);
        }
        var role = roleStore.create(key, request.name().trim(), request.description());
        auditRecorder.record(null, currentActor(), "CREATE", "role", key, "role " + key, null);
        return role;
    }

    @PutMapping("/{roleKey}")
    public Role update(@PathVariable("roleKey") String roleKey, @RequestBody UpdateRoleRequest request) {
        var key = normalizeKey(roleKey);
        if (roleStore.findByKey(key) == null) {
            throw new IllegalArgumentException("role does not exist: " + key);
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        var role = roleStore.update(key, request.name().trim(), request.description());
        auditRecorder.record(null, currentActor(), "UPDATE", "role", key, "role " + key, null);
        return role;
    }

    @DeleteMapping("/{roleKey}")
    public void delete(@PathVariable("roleKey") String roleKey) {
        var key = normalizeKey(roleKey);
        var role = roleStore.findByKey(key);
        if (role == null) {
            throw new IllegalArgumentException("role does not exist: " + key);
        }
        if (role.builtin()) {
            throw new IllegalArgumentException("builtin role cannot be deleted: " + key);
        }
        if (roleStore.countUsers(key) > 0) {
            throw new IllegalArgumentException("role is still assigned to users: " + key);
        }
        roleStore.delete(key);
        auditRecorder.record(null, currentActor(), "DELETE", "role", key, "role " + key, null);
    }

    private static String normalizeKey(String key) {
        return key == null ? null : key.trim().toUpperCase();
    }

    private static String currentActor() {
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception exception) {
            return null;
        }
    }
}
