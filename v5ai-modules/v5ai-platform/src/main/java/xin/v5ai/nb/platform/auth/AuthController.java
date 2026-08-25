package xin.v5ai.nb.platform.auth;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.platform.Menu;
import xin.v5ai.nb.common.core.platform.MenuStore;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.common.satoken.utils.LoginHelper;
import xin.v5ai.nb.platform.domain.vo.LoginVo;
import xin.v5ai.nb.platform.service.impl.LoginServiceImpl;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginServiceImpl loginService;
    private final UserAccountStore userAccountStore;
    private final MenuStore menuStore;

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
    @GetMapping("/me")
    public R<LoginResponse> me() {
        var username = LoginHelper.getUsername();
        var account = userAccountStore.findByUsername(username);
        if (account == null) {
            throw new IllegalArgumentException("current account does not exist");
        }
        var roles = account.roles() == null ? java.util.List.<String>of() : account.roles();
        return R.ok(new LoginResponse("Bearer", StpUtil.getTokenValue(), account.id(), account.username(),
                account.displayName(), account.tenantId(), roles));
    }

    /**
     * 当前登录用户可见菜单（用户 → 角色 → 菜单 并集 + 祖先补齐）。
     */
    @GetMapping("/menus")
    public R<List<Menu>> menus() {
        var username = LoginHelper.getUsername();
        var account = userAccountStore.findByUsername(username);
        if (account == null) {
            throw new IllegalArgumentException("current account does not exist");
        }
        var roles = account.roles() == null ? List.<String>of() : account.roles();
        return R.ok(menuStore.listByRoleKeys(roles));
    }
}
