package xin.v5ai.nb.platform.api;

import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;

/**
 * 运行期 API Key 鉴权结果在请求里的传递约定：过滤器（platform）鉴权后把
 * {@link ApiKeysAuthDTO} 放进请求属性，同一请求内的运行时控制器（v5ai-runtime）
 * 可直接取用，避免重复校验密钥。
 *
 * <p>该常量类放在端口模块（v5ai-api），让 filters 与 controllers 不必互相依赖。</p>
 */
public final class ApiKeyAuthAttributes {

    /**
     * 请求属性名：值为 {@link ApiKeysAuthDTO}。
     */
    public static final String REQUEST_ATTRIBUTE = "v5ai.apiKeyAuth";

    private ApiKeyAuthAttributes() {
    }
}
