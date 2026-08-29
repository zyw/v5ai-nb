//package xin.v5ai.nb.platform.controller.auth;
//
//import jakarta.validation.constraints.NotBlank;
//import jakarta.validation.constraints.NotNull;
//
///**
// * 滑块验证码校验请求体。
// *
// * @param uuid 拼图令牌唯一标识
// * @param x    用户拖动后拼图块的横向位置（像素）
// */
//public record CaptchaVerifyBody(
//        @NotBlank(message = "验证码标识不能为空") String uuid,
//        @NotNull(message = "滑块位置不能为空") Double x
//) {
//}
