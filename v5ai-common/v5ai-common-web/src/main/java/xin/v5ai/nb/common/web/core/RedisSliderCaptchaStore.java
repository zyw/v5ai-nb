package xin.v5ai.nb.common.web.core;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.redis.utils.RedisUtils;

import java.time.Duration;

/**
 * 基于 Redis 的滑块验证码令牌存储实现。
 */
@Service
public class RedisSliderCaptchaStore implements SliderCaptchaStore {

    private static final String GAP_KEY = "captcha:slider:gap:";
    private static final String VERIFIED_KEY = "captcha:slider:ok:";

    @Override
    public void saveGap(String uuid, int gapX, Duration ttl) {
        RedisUtils.setCacheObject(GAP_KEY + uuid, gapX, ttl);
    }

    @Override
    public Integer getGap(String uuid) {
        return RedisUtils.getCacheObject(GAP_KEY + uuid);
    }

    @Override
    public void deleteGap(String uuid) {
        RedisUtils.deleteObject(GAP_KEY + uuid);
    }

    @Override
    public void markVerified(String uuid, Duration ttl) {
        RedisUtils.setCacheObject(VERIFIED_KEY + uuid, Boolean.TRUE, ttl);
    }

    @Override
    public boolean checkAndConsumeVerified(String uuid) {
        return RedisUtils.deleteObject(VERIFIED_KEY + uuid);
    }
}
