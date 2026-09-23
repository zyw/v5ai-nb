package xin.v5ai.nb.common.satoken.token;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.redis.utils.RedisUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 刷新令牌服务：登录时签发，/api/auth/refresh 消费轮换，退出登录时吊销。
 * 令牌以随机 UUID 存储于 Redis，服务端不保存可猜测的签名信息。
 */
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";

    /**
     * 刷新令牌有效期（分钟），默认 43200（30 天）。
     */
    @Value("${v5ai.auth.refresh-token-ttl:43200}")
    private long ttlMinutes;

    /**
     * 签发刷新令牌。
     *
     * @param userId   用户ID
     * @param clientId 客户端ID
     * @return 刷新令牌
     */
    public String issue(Long userId, String clientId) {
        String token = IdUtil.fastSimpleUUID();
        RedisUtils.setCacheObject(KEY_PREFIX + token, userId + ":" + clientId, Duration.ofMinutes(ttlMinutes));
        return token;
    }

    /**
     * 消费刷新令牌（一次性：读取即删除，配合令牌轮换）。
     *
     * @param refreshToken 刷新令牌
     * @return 令牌负载，无效或已过期返回 null
     */
    public RefreshTokenInfo consume(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            return null;
        }
        String key = KEY_PREFIX + refreshToken;
        Object raw = RedisUtils.getCacheObject(key);
        RedisUtils.deleteObject(key);
        if (raw == null) {
            return null;
        }
        String payload = raw.toString();
        int sep = payload.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        try {
            return new RefreshTokenInfo(Long.parseLong(payload.substring(0, sep)), payload.substring(sep + 1));
        } catch (NumberFormatException e) {
            log.warn("刷新令牌负载格式异常: {}", payload);
            return null;
        }
    }

    /**
     * 吊销刷新令牌（退出登录时调用）。
     *
     * @param refreshToken 刷新令牌
     */
    public void revoke(String refreshToken) {
        if (StringUtils.isNotBlank(refreshToken)) {
            RedisUtils.deleteObject(KEY_PREFIX + refreshToken);
        }
    }

    /**
     * 刷新令牌有效期（秒），用于登录/刷新响应中的 refresh_expire_in。
     *
     * @return 秒
     */
    public long ttlSeconds() {
        return TimeUnit.MINUTES.toSeconds(Math.max(ttlMinutes, 1));
    }
}
