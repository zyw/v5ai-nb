//package xin.v5ai.nb.platform.controller;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.web.bind.annotation.*;
//import xin.v5ai.nb.common.core.domain.R;
//import xin.v5ai.nb.common.core.domain.PageResult;
//import xin.v5ai.nb.common.core.platform.UserAccount;
//import xin.v5ai.nb.common.core.platform.UserAccountStore;
//import xin.v5ai.nb.common.log.annotation.Log;
//import xin.v5ai.nb.common.log.enums.BusinessType;
//import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
//
//import java.util.List;
//
///**
// * 用户管理 API（RBAC）。
// */
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/api/admin/users")
//public class UserController {
//    private final UserAccountStore userAccountStore;
//    private final PasswordEncoder passwordEncoder;
//
//    public record CreateUserRequest(String username, String password, String displayName,
//                                    Long tenantId, List<String> roles) {
//    }
//
//    public record UpdateRolesRequest(List<String> roles) {
//    }
//
//    public record UpdateStatusRequest(String status) {
//    }
//
//    @GetMapping
//    public R<PageResult<UserAccount>> listUsers(PageQuery pageQuery) {
////      TODO PageUtils.page  return R.ok(PageUtils.page(userAccountStore.list(), pageQuery));
//        return R.ok();
//    }
//
//    @PostMapping
//    @Log(title = "新建用户", businessType = BusinessType.INSERT)
//    public R<UserAccount> createUser(@RequestBody CreateUserRequest request) {
//        if (request.username() == null || request.username().isBlank()
//                || request.password() == null || request.password().length() < 4) {
//            throw new IllegalArgumentException("username and a password of at least 4 characters are required");
//        }
//        if (userAccountStore.findByUsername(request.username().trim()) != null) {
//            throw new IllegalArgumentException("username already exists: " + request.username());
//        }
//        var roles = request.roles() == null || request.roles().isEmpty()
//                ? List.of("USER") : request.roles();
//        var account = userAccountStore.create(request.username().trim(),
//                passwordEncoder.encode(request.password()), request.displayName(), request.tenantId(), roles);
//        return R.ok(account);
//    }
//
//    @PutMapping("/{id}/roles")
//    @Log(title = "更新用户角色", businessType = BusinessType.GRANT)
//    public R<UserAccount> updateRoles(@PathVariable("id") Long id, @RequestBody UpdateRolesRequest request) {
//        if (request.roles() == null || request.roles().isEmpty()) {
//            throw new IllegalArgumentException("roles must not be empty");
//        }
//        userAccountStore.updateRoles(id, request.roles());
//        return R.ok(userAccountStore.findById(id));
//    }
//
//    @PutMapping("/{id}/status")
//    @Log(title = "更新用户状态", businessType = BusinessType.UPDATE)
//    public R<UserAccount> updateStatus(@PathVariable("id") Long id, @RequestBody UpdateStatusRequest request) {
//        if (request.status() == null || !("0".equals(request.status()) || "1".equals(request.status()))) {
//            throw new IllegalArgumentException("status must be '0' (normal) or '1' (disabled)");
//        }
//        userAccountStore.setStatus(id, request.status());
//        return R.ok(userAccountStore.findById(id));
//    }
//}
