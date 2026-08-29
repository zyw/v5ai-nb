package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.web.core.SliderCaptchaStore;
import xin.v5ai.nb.platform.domain.VerifyEnum;
import xin.v5ai.nb.platform.domain.vo.SliderCaptchaVO;

/**
 * 图片滑块验证码服务：生成拼图与缺口，校验用户拖动位置。
 * 缺口 x 坐标仅存于服务端（{@link SliderCaptchaStore}），客户端无法获取。
 */
public interface IPlmSliderCaptchaService {

    /**
     * 登录需先通过滑块验证的错误码（前端据此弹出滑块组件）。
     */
    int CAPTCHA_REQUIRED_CODE = 1006;

    /**
     * 背景图宽度。
     */
    int WIDTH = 320;

    /**
     * 背景图高度。
     */
    int HEIGHT = 160;

    /**
     * 拼图块尺寸（正方形边长）。
     */
    int PIECE_SIZE = 40;

    /**
     * 缺口位置允许误差（像素）。
     */
    int TOLERANCE = 5;

    /**
     * 生成一张滑块拼图：随机缺口位置，背景图与拼图块以 PNG base64 返回。
     *
     * @return 拼图令牌与图片数据
     */
    SliderCaptchaVO generate();

    /**
     * 校验用户拖动位置：与缺口 x 坐标误差不超过 {@link #TOLERANCE} 视为通过。
     * 校验通过后标记该令牌已验证，供登录门控消费；拼图一次性使用，失败或过期需刷新。
     *
     * @param uuid 拼图令牌
     * @param x    用户拖动后拼图块的横向位置
     * @return 校验结果
     */
    VerifyEnum verify(String uuid, Double x);

    /**
     * 登录门控：消费已验证标记。返回 false 表示该令牌未通过滑块验证。
     *
     * @param uuid 拼图令牌
     * @return 是否存在且已消费已验证标记
     */
    boolean checkAndConsumeVerified(String uuid);
}
