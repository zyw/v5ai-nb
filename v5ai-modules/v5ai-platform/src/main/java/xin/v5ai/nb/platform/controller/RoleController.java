//package xin.v5ai.nb.platform.controller;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.*;
//import xin.v5ai.nb.common.core.domain.R;
//import xin.v5ai.nb.common.core.domain.PageResult;
//import xin.v5ai.nb.common.core.platform.MenuStore;
//import xin.v5ai.nb.common.core.platform.Role;
//import xin.v5ai.nb.common.log.annotation.Log;
//import xin.v5ai.nb.common.log.enums.BusinessType;
//import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
//
//import java.util.List;
//
///**
// * 角色管理 API（角色定义 CRUD）。
// * 内置角色（ADMIN / USER）不可删除；仍被用户占用的角色不可删除。
// */
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/api/admin/roles")
//public class RoleController {
//
//    private final IRoleService roleService;
//    private final MenuStore menuStore;
//
//    public record CreateRoleRequest(String roleKey, String roleName, String description, String dataScope) {
//    }
//
//    public record UpdateRoleRequest(String roleName, String description, String dataScope) {
//    }
//
//    public record UpdateRoleMenusRequest(List<Long> menuIds) {
//    }
//
//    @GetMapping
//    public R<PageResult<Role>> list(PageQuery pageQuery) {
////      TODO PageUtils.page  return R.ok(PageUtils.page(roleService.list(), pageQuery));
//        return R.ok();
//    }
//
//    @PostMapping
//    @Log(title = "新建角色", businessType = BusinessType.INSERT)
//    public R<Role> create(@RequestBody CreateRoleRequest request) {
//        var key = normalizeKey(request.roleKey());
//        if (key == null || key.isBlank()) {
//            throw new IllegalArgumentException("roleKey is required");
//        }
//        if (!key.matches("[A-Z][A-Z0-9_]*")) {
//            throw new IllegalArgumentException("roleKey must be uppercase letters, digits or underscore");
//        }
//        if (request.roleName() == null || request.roleName().isBlank()) {
//            throw new IllegalArgumentException("roleName is required");
//        }
//        validateDataScope(request.dataScope());
//        if (roleService.findByKey(key) != null) {
//            throw new IllegalArgumentException("role already exists: " + key);
//        }
//        var role = roleService.create(key, request.roleName().trim(), request.description(), request.dataScope());
//        return R.ok(role);
//    }
//
//    @PutMapping("/{roleKey}")
//    @Log(title = "更新角色", businessType = BusinessType.UPDATE)
//    public R<Role> update(@PathVariable("roleKey") String roleKey, @RequestBody UpdateRoleRequest request) {
//        var key = normalizeKey(roleKey);
//        if (roleService.findByKey(key) == null) {
//            throw new IllegalArgumentException("role does not exist: " + key);
//        }
//        if (request.roleName() == null || request.roleName().isBlank()) {
//            throw new IllegalArgumentException("roleName is required");
//        }
//        validateDataScope(request.dataScope());
//        var role = roleService.update(key, request.roleName().trim(), request.description(), request.dataScope());
//        return R.ok(role);
//    }
//
//    @DeleteMapping("/{roleKey}")
//    @Log(title = "删除角色", businessType = BusinessType.DELETE)
//    public R<Void> delete(@PathVariable("roleKey") String roleKey) {
//        var key = normalizeKey(roleKey);
//        var role = roleService.findByKey(key);
//        if (role == null) {
//            throw new IllegalArgumentException("role does not exist: " + key);
//        }
//        if (role.builtin()) {
//            throw new IllegalArgumentException("builtin role cannot be deleted: " + key);
//        }
//        if (roleService.countUsers(key) > 0) {
//            throw new IllegalArgumentException("role is still assigned to users: " + key);
//        }
//        roleService.delete(key);
//        return R.ok();
//    }
//
//    @GetMapping("/{roleKey}/menus")
//    public R<List<Long>> listMenus(@PathVariable("roleKey") String roleKey) {
//        var key = normalizeKey(roleKey);
//        if (roleService.findByKey(key) == null) {
//            throw new IllegalArgumentException("role does not exist: " + key);
//        }
//        return R.ok(menuStore.listMenuIdsByRole(key));
//    }
//
//    @PutMapping("/{roleKey}/menus")
//    @Log(title = "分配角色菜单", businessType = BusinessType.GRANT)
//    public R<Void> updateMenus(@PathVariable("roleKey") String roleKey, @RequestBody UpdateRoleMenusRequest request) {
//        var key = normalizeKey(roleKey);
//        if (roleService.findByKey(key) == null) {
//            throw new IllegalArgumentException("role does not exist: " + key);
//        }
//        var menuIds = request.menuIds() == null ? List.<Long>of() : request.menuIds();
//        menuStore.replaceRoleMenus(key, menuIds);
//        return R.ok();
//    }
//
//    private static String normalizeKey(String key) {
//        return key == null ? null : key.trim().toUpperCase();
//    }
//
//    private static void validateDataScope(String dataScope) {
//        if (dataScope == null || dataScope.isBlank()) {
//            throw new IllegalArgumentException("dataScope is required");
//        }
//        if (!dataScope.matches("[1-6]")) {
//            throw new IllegalArgumentException("dataScope must be 1-6 (1全部 2自定 3本部门 4本部门及以下 5仅本人 6部门及以下或本人)");
//        }
//    }
//}
