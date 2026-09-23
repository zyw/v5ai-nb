package xin.v5ai.nb.platform.api;

import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;

/**
 * API Key 鉴权服务（运行期接口 {@code /api/v1/agents/{agentKey}/...} 的密钥校验端口）。
 */
public interface ApiKeysService {

    /**
     * 校验请求携带的明文 API Key，并返回该 Key 的归属与可访问 Agent。
     *
     * <p>实现按明文 Key 内嵌的 tracking id 定位密钥行，再做 BCrypt 全串校验；
     * 密钥不存在、已停用或校验失败时返回 {@code null}。</p>
     *
     * @param apiKey 请求头 {@code Authorization: Bearer &lt;api-key&gt;} 中的明文 Key
     * @return 校验通过时返回密钥归属与绑定信息，否则返回 null
     */
    ApiKeysAuthDTO authenticate(String apiKey);
}