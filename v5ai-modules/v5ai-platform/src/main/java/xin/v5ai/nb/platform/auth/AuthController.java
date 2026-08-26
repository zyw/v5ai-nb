package xin.v5ai.nb.platform.auth;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.LoginUser;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.platform.MenuStore;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.common.mybatis.helper.DataPermissionHelper;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.domain.Menu;
import xin.v5ai.nb.platform.domain.vo.LoginVo;
import xin.v5ai.nb.platform.domain.vo.RouterVo;
import xin.v5ai.nb.platform.domain.vo.UserInfoVo;
import xin.v5ai.nb.platform.domain.vo.UserVo;
import xin.v5ai.nb.platform.service.IMenuService;
import xin.v5ai.nb.platform.service.IUserService;
import xin.v5ai.nb.platform.service.impl.LoginServiceImpl;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginServiceImpl loginService;
    private final UserAccountStore userAccountStore;
    private final MenuStore menuStore;

    private final IUserService userService;
    private final IMenuService menuService;

    @PostMapping("/login")
    public R<LoginVo> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(loginService.login(request));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        loginService.logout();
        return R.ok();
    }

    /**
     * 当前登录用户信息（需已登录）。
     */
/*    @GetMapping("/me")
    public R<LoginResponse> me() {
        var username = LoginHelper.getUsername();
        var account = userAccountStore.findByUsername(username);
        if (account == null) {
            throw new IllegalArgumentException("current account does not exist");
        }
        var roles = account.roles() == null ? java.util.List.<String>of() : account.roles();
        return R.ok(new LoginResponse("Bearer", StpUtil.getTokenValue(), account.id(), account.username(),
                account.displayName(), account.tenantId(), roles));
    }*/

    /**
     * 获取用户信息
     *
     * @return 当前登录用户信息、角色与权限集合
     */
    @GetMapping("/getInfo")
    public R<UserInfoVo> getInfo() {
        UserInfoVo userInfoVo = new UserInfoVo();
        LoginUser loginUser = LoginHelper.getLoginUser();

        UserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
        if (ObjectUtil.isNull(user)) {
            return R.fail("没有权限访问用户数据!");
        }
        userInfoVo.setUser(user);
        userInfoVo.setPermissions(loginUser.getMenuPermission());
        userInfoVo.setRoles(loginUser.getRolePermission());
        return R.ok(userInfoVo);
    }

    /**
     * 获取路由信息
     *
     * @return 当前用户可访问的路由信息
     */
    @GetMapping("/getRouters")
    public R<List<RouterVo>> getRouters() {
        List<Menu> menus = menuService.selectMenuTreeByUserId(LoginHelper.getUserId());
        return R.ok(menuService.buildMenus(menus));
    }

    /**
     * 当前登录用户可见菜单（用户 → 角色 → 菜单 并集 + 祖先补齐）。
     */
//    @GetMapping("/menus")
//    public R<List<Menu>> menus() {
//        var username = LoginHelper.getUsername();
//        var account = userAccountStore.findByUsername(username);
//        if (account == null) {
//            throw new IllegalArgumentException("current account does not exist");
//        }
//        var roles = account.roles() == null ? List.<String>of() : account.roles();
//        return R.ok(menuStore.listByRoleKeys(roles));
//    }
}
