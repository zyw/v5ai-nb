package xin.v5ai.nb.platform.controller.auth;

import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.json.utils.JsonUtils;
import xin.v5ai.nb.common.mybatis.helper.DataPermissionHelper;
import xin.v5ai.nb.common.satoken.token.RefreshTokenService;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.api.model.LoginUser;
import xin.v5ai.nb.platform.domain.PlmMenu;
import xin.v5ai.nb.platform.domain.bo.RefreshBody;
import xin.v5ai.nb.platform.domain.vo.PlmUserVo;
import xin.v5ai.nb.platform.domain.vo.RouterVo;
import xin.v5ai.nb.platform.domain.vo.UserInfoVo;
import xin.v5ai.nb.platform.service.ILoginService;
import xin.v5ai.nb.platform.service.IPlmMenuService;
import xin.v5ai.nb.platform.service.IPlmUserService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

//    private final LoginServiceImpl loginService;
//    private final UserAccountStore userAccountStore;
//    private final MenuStore menuStore;



    // ================== 新 =======================
    private final ILoginService loginService;
    private final IPlmUserService userService;
    private final IPlmMenuService menuService;
    private final RefreshTokenService refreshTokenService;



//    @PostMapping("/logout")
//    public R<Void> logout() {
//        loginService.logout();
//        return R.ok();
//    }

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
                account.displayName(), roles));
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

        PlmUserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
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
        List<PlmMenu> menus = menuService.selectMenuTreeByUserId(LoginHelper.getUserId());
        return R.ok(menuService.buildMenus(menus));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<Void> logout(@RequestBody(required = false) String body) {
        // 可选：携带刷新令牌一并吊销
        if (StringUtils.isNotBlank(body)) {
            RefreshBody refreshBody = JsonUtils.parseObject(body, RefreshBody.class);
            if (ObjectUtil.isNotNull(refreshBody)) {
                refreshTokenService.revoke(refreshBody.refreshToken());
            }
        }
        loginService.logout();
        return R.ok("退出成功");
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
