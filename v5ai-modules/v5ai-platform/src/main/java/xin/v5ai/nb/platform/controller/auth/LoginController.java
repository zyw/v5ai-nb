package xin.v5ai.nb.platform.controller.auth;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.constant.Constants;
import xin.v5ai.nb.common.core.constant.GlobalConstants;
import xin.v5ai.nb.common.core.constant.SystemConstants;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.domain.model.LoginBody;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.core.utils.MessageUtils;
import xin.v5ai.nb.common.core.utils.SpringUtils;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.core.utils.ValidatorUtils;
import xin.v5ai.nb.common.encrypt.annotation.ApiEncrypt;
import xin.v5ai.nb.common.json.utils.JsonUtils;
import xin.v5ai.nb.common.redis.annotation.RateLimiter;
import xin.v5ai.nb.common.redis.enums.LimitType;
import xin.v5ai.nb.common.redis.utils.RedisUtils;
import xin.v5ai.nb.common.web.config.properties.CaptchaProperties;
import xin.v5ai.nb.common.web.core.BaseController;
import xin.v5ai.nb.common.web.core.WaveAndCircleCaptcha;
import xin.v5ai.nb.platform.domain.VerifyEnum;
import xin.v5ai.nb.platform.domain.bo.RefreshBody;
import xin.v5ai.nb.platform.domain.vo.LoginVo;
import xin.v5ai.nb.platform.domain.vo.PlmClientVo;
import xin.v5ai.nb.platform.domain.vo.SliderCaptchaVO;
import xin.v5ai.nb.platform.service.ILoginService;
import xin.v5ai.nb.platform.service.IPlmClientService;
import xin.v5ai.nb.platform.service.IPlmSliderCaptchaService;

import java.awt.*;
import java.time.Duration;

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
    private final CaptchaProperties captchaProperties;

    private final IPlmSliderCaptchaService sliderCaptchaService;

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

    /**
     * 刷新令牌：换取新的访问令牌（旧访问令牌因 is-concurrent=false 自动失效）。
     */
    @ApiEncrypt
    @PostMapping("/refresh")
    public R<LoginVo> refresh(@RequestBody String body) {
        RefreshBody refreshBody = JsonUtils.parseObject(body, RefreshBody.class);
        ValidatorUtils.validate(refreshBody);
        return R.ok(loginService.refresh(refreshBody.refreshToken()));
    }

    /**
     * 获取滑块拼图。
     *
     * @return 拼图令牌与背景图/拼图块（PNG base64）
     */
    @GetMapping("/captcha")
    public R<SliderCaptchaVO> captcha() {
        return R.ok(sliderCaptchaService.generate());
    }

    /**
     * 校验滑块拼图位置，通过后该令牌可用于登录。
     *
     * @param body 拼图令牌与拖动位置
     * @return 校验结果
     */
    @PostMapping("/captcha/verify")
    public R<Void> verify(@RequestBody CaptchaVerifyBo body) {
        ValidatorUtils.validate(body);
        VerifyEnum result = sliderCaptchaService.verify(body.uuid(), body.x());
        if (result == VerifyEnum.EXPIRED) {
            throw new ServiceException(MessageUtils.message("captcha.expired"));
        }
        if (result == VerifyEnum.MISMATCH) {
            throw new ServiceException(MessageUtils.message("captcha.error"));
        }
        return R.ok();
    }


    /**
     * 获取图片验证码。
     *
     * @return 验证码信息
     */
    @GetMapping("/code")
    public R<CaptchaVo> getCode() {
        boolean captchaEnabled = captchaProperties.getEnable();
        if (!captchaEnabled) {
            return R.ok(new CaptchaVo(false, null, null));
        }
        return R.ok(SpringUtils.getAopProxy(this).getCodeImpl());
    }

    /**
     * 实际生成图片验证码并缓存结果。
     *
     * @return 验证码信息
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo getCodeImpl() {
        // 保存验证码信息
        String uuid = IdUtil.simpleUUID();
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + uuid;
        // 生成验证码
        String captchaType = captchaProperties.getType();
        CodeGenerator codeGenerator;
        if ("math".equals(captchaType)) {
            codeGenerator = new MathGenerator(captchaProperties.getNumberLength(), false);
        } else {
            codeGenerator = new RandomGenerator(captchaProperties.getCharLength());
        }
        WaveAndCircleCaptcha captcha = new WaveAndCircleCaptcha(160, 60);
        // captcha.setBackground(Color.WHITE); // 不设置就是透明底
        captcha.setFont(new Font("Arial", Font.BOLD, 45));
        captcha.setGenerator(codeGenerator);
        captcha.createCode();
        // 如果是数学验证码，使用SpEL表达式处理验证码结果
        String code = captcha.getCode();
        if ("math".equals(captchaType)) {
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(StringUtils.remove(code, "="));
            code = exp.getValue(String.class);
        }
        RedisUtils.setCacheObject(verifyKey, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        return new CaptchaVo(true, uuid, captcha.getImageBase64());
    }

    /**
     * 滑块验证码校验请求体。
     *
     * @param uuid 拼图令牌唯一标识
     * @param x    用户拖动后拼图块的横向位置（像素）
     */
    public record CaptchaVerifyBo(
            @NotBlank(message = "验证码标识不能为空") String uuid,
            @NotNull(message = "滑块位置不能为空") Double x
    ) {
    }

    /**
     * 图片验证码响应对象。
     *
     * @param captchaEnabled 是否启用验证码
     * @param uuid           验证码标识
     * @param img            Base64 图片数据
     */
    public record CaptchaVo(Boolean captchaEnabled, String uuid, String img) {
    }
}
