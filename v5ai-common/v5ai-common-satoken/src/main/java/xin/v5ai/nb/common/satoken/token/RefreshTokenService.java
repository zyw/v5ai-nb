package xin.v5ai.nb.common.satoken.token;

public interface RefreshTokenService {
    /**
     * 签发刷新令牌。
     *
     * @param userId   用户ID
     * @param clientId 客户端ID
     * @return 刷新令牌
     */
    String issue(Long userId, String clientId);

    /**
     * 消费刷新令牌（一次性：读取即删除，配合令牌轮换）。
     *
     * @param refreshToken 刷新令牌
     * @return 令牌负载，无效或已过期返回 null
     */
    RefreshTokenInfo consume(String refreshToken);

    /**
     * 吊销刷新令牌（退出登录时调用）。
     *
     * @param refreshToken 刷新令牌
     */
    void revoke(String refreshToken);

    /**
     * 刷新令牌有效期（秒），用于登录/刷新响应中的 refresh_expire_in。
     *
     * @return 秒
     */
    long ttlSeconds();

    /**
     * 刷新令牌负载：用户ID与客户端ID。
     */
    record RefreshTokenInfo(Long userId, String clientId) {
    }
}
