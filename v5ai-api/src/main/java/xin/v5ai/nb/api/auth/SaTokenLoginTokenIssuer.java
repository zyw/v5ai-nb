package xin.v5ai.nb.api.auth;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class SaTokenLoginTokenIssuer implements LoginTokenIssuer {
    @Override
    public String issue(String username) {
        StpUtil.login(username);
        return StpUtil.getTokenValue();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
