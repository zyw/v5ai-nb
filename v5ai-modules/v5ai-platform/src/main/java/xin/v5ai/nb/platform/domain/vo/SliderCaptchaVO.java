package xin.v5ai.nb.platform.domain.vo;

/**
 * 滑块验证码返回对象。
 *
 * @param uuid       拼图令牌唯一标识（verify 与登录时回传）
 * @param background 背景图（PNG base64，不含 data: 前缀）
 * @param puzzle     拼图块（PNG base64，不含 data: 前缀）
 * @param y          拼图块应放置的纵向位置（缺口 y 坐标，横向位置由用户拖动决定）
 */
public record SliderCaptchaVO(
        String uuid,
        String background,
        String puzzle,
        Integer y
) {
}
