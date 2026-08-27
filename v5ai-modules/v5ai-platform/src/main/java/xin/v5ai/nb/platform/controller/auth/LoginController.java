package xin.v5ai.nb.platform.controller.auth;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.model.LoginBody;
import xin.v5ai.nb.common.core.utils.MessageUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.core.utils.ValidatorUtils;
import xin.v5ai.nb.common.encrypt.annotation.ApiEncrypt;
import xin.v5ai.nb.common.json.utils.JsonUtils;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.platform.domain.vo.LoginVo;
import xin.v5ai.nb.platform.domain.vo.PlmClientVo;
import xin.v5ai.nb.platform.service.ILoginService;
import xin.v5ai.nb.platform.service.IPlmClientService;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 登录、注册相关页面接口控制器
 * 登录接口，获取验证码接口，注册接口等不需要验证的接口
 */
@Slf4j
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class LoginController extends BaseController {

    private final IPlmClientService clientService;
    private final ILoginService loginService;

    /**
     * 登录
     */
    @ApiEncrypt
    @PostMapping("/login")
    public R<LoginVo> login(@RequestBody String body) {
        LoginBody loginBody = JsonUtils.parseObject(body, LoginBody.class);
        ValidatorUtils.validate(loginBody);
        // 授权类型和客户端id
        String clientId = loginBody.getClientId();
        String grantType = loginBody.getGrantType();
        PlmClientVo client = clientService.queryByClientId(clientId);
        // 查询不到 client 或 client 内不包含 grantType
        if (ObjectUtil.isNull(client) || !StringUtils.contains(client.getGrantType(), grantType)) {
            log.info("客户端id: {} 认证类型：{} 异常!.", clientId, grantType);
            return R.fail(MessageUtils.message("auth.grant.type.error"));
        } else if (!SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail(MessageUtils.message("auth.grant.type.blocked"));
        }
        // 登录
        LoginVo loginVo = loginService.login(body, client);

        return R.ok(loginVo);
    }
}
