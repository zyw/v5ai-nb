package xin.v5ai.nb.platform.api;

/**
 * Api Key服务
 */
public interface ApiKeyService {
    /**
     * 检验 Api Key是否匹配
     * @param agentKey
     * @param apiKey
     */
    boolean checkApiKey(String agentKey, String apiKey);
}
