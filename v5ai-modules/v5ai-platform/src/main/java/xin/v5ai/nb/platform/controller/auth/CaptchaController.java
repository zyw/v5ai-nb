//package xin.v5ai.nb.platform.controller.auth;
//
//import cn.dev33.satoken.annotation.SaIgnore;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import xin.v5ai.nb.common.core.domain.R;
//import xin.v5ai.nb.common.core.exception.ServiceException;
//import xin.v5ai.nb.common.core.utils.MessageUtils;
//import xin.v5ai.nb.common.core.utils.ValidatorUtils;
//import xin.v5ai.nb.platform.domain.vo.SliderCaptchaVO;
//import xin.v5ai.nb.platform.service.SliderCaptchaService;
//
///**
// * 登录滑块验证码接口：生成拼图与校验拖动位置，均无需登录。
// */
//@SaIgnore
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/api/auth")
//public class CaptchaController {
//
//    private final SliderCaptchaService sliderCaptchaService;
//
//    /**
//     * 获取滑块拼图。
//     *
//     * @return 拼图令牌与背景图/拼图块（PNG base64）
//     */
//    @GetMapping("/captcha")
//    public R<SliderCaptchaVO> captcha() {
//        return R.ok(sliderCaptchaService.generate());
//    }
//
//    /**
//     * 校验滑块拼图位置，通过后该令牌可用于登录。
//     *
//     * @param body 拼图令牌与拖动位置
//     * @return 校验结果
//     */
//    @PostMapping("/captcha/verify")
//    public R<Void> verify(@RequestBody CaptchaVerifyBody body) {
//        ValidatorUtils.validate(body);
//        SliderCaptchaService.VerifyResult result = sliderCaptchaService.verify(body.uuid(), body.x());
//        if (result == SliderCaptchaService.VerifyResult.EXPIRED) {
//            throw new ServiceException(MessageUtils.message("captcha.expired"));
//        }
//        if (result == SliderCaptchaService.VerifyResult.MISMATCH) {
//            throw new ServiceException(MessageUtils.message("captcha.error"));
//        }
//        return R.ok();
//    }
//}
