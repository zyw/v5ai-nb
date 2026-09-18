package xin.v5ai.nb.common.encrypt.apikeys;

import xin.v5ai.nb.common.core.domain.model.Tuple2;

public interface AgentApiKeysVerifier {
    /**
     * 验证ApiKey
     * @param apiKey 接收的ApiKey
     * @param secretHash 数据库中的密文
     * @return 是否验证通过
     */
    boolean verify(String apiKey,String secretHash);

    /**
     * 生成ApiKey
     * @return first: 生成的ApiKey second: 生成的ApiKey的密文
     */
    Tuple2<String, String> generateApiKey();

    /**
     * 取明文 ApiKey 内嵌的 tracking id（跟踪 ID）。
     *
     * <p>密文（BCrypt）无法反查密钥行，明文 Key 因此内嵌一个 UUID 作为定位手柄
     * （明文形如 {@code v5ai_<UUID 36 位><32 位随机主体>}）：
     * 运行时先用它命中 v5ai_api_keys.tracking_id 唯一索引，再做全串 BCrypt 校验。</p>
     *
     * @param apiKey 明文 ApiKey
     * @return tracking id；key 为空或不符合生成格式时返回 null
     */
    String trackingIdOf(String apiKey);
}
