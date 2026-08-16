package xin.v5ai.nb.api.auth;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.platform.Menu;
import xin.v5ai.nb.common.platform.MenuStore;
import xin.v5ai.nb.common.platform.UserAccountStore;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final V5aiLoginService loginService;
    private final UserAccountStore userAccountStore;
    private final MenuStore menuStore;

    public AuthController(V5aiLoginService loginService, UserAccountStore userAccountStore, MenuStore menuStore) {
        this.loginService = loginService;
        this.userAccountStore = userAccountStore;
        this.menuStore = menuStore;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @PostMapping("/logout")
    public void logout() {
        loginService.logout();
    }

    /**
     * 当前登录用户信息（需已登录）。
     */
    @GetMapping("/me")
    public LoginResponse me() {
        var username = StpUtil.getLoginIdAsString();
        var account = userAccountStore.findByUsername(username);
        if (account == null) {
            throw new IllegalArgumentException("current account does not exist");
        }
        var roles = account.roles() == null ? java.util.List.<String>of() : account.roles();
        return new LoginResponse("Bearer", StpUtil.getTokenValue(), account.id(), account.username(),
                account.displayName(), account.tenantId(), roles);
    }

    /**
     * 当前登录用户可见菜单（用户 → 角色 → 菜单 并集 + 祖先补齐）。
     */
    @GetMapping("/menus")
    public List<Menu> menus() {
        var username = StpUtil.getLoginIdAsString();
        var account = userAccountStore.findByUsername(username);
        if (account == null) {
            throw new IllegalArgumentException("current account does not exist");
        }
        var roles = account.roles() == null ? List.<String>of() : account.roles();
        return menuStore.listByRoleKeys(roles);
    }
}
