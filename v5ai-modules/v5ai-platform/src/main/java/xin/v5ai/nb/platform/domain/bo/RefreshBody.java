package xin.v5ai.nb.platform.domain.bo;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求体。
 *
 * @param refreshToken 刷新令牌
 */
public record RefreshBody(
        @NotBlank(message = "刷新令牌不能为空") String refreshToken
) {
}
