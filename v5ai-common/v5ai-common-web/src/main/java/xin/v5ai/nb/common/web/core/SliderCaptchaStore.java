package xin.v5ai.nb.common.web.core;

import java.time.Duration;

/**
 * 滑块验证码令牌存储端口（缺口位置、已验证标记）。
 * 缺口位置只在服务端保存，客户端仅持有拼图图片，横向对齐由服务端校验。
 */
public interface SliderCaptchaStore {

    /**
     * 保存拼图缺口 x 坐标。
     *
     * @param uuid 拼图令牌
     * @param gapX 缺口横向位置
     * @param ttl  令牌有效期
     */
    void saveGap(String uuid, int gapX, Duration ttl);

    /**
     * 读取拼图缺口 x 坐标。
     *
     * @param uuid 拼图令牌
     * @return 缺口 x 坐标，不存在或已过期返回 null
     */
    Integer getGap(String uuid);

    /**
     * 删除拼图缺口（一次性使用）。
     *
     * @param uuid 拼图令牌
     */
    void deleteGap(String uuid);

    /**
     * 标记拼图已验证（校验通过后可凭此通过登录门控）。
     *
     * @param uuid 拼图令牌
     * @param ttl  标记有效期
     */
    void markVerified(String uuid, Duration ttl);

    /**
     * 校验并消费已验证标记（一次性）。
     *
     * @param uuid 拼图令牌
     * @return 标记存在且已被消费返回 true，否则 false
     */
    boolean checkAndConsumeVerified(String uuid);
}
