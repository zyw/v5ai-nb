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
     * 取明文 ApiKey 的 SHA-256 十六进制摘要（库内定位手柄）。
     *
     * <p>明文 Key 形如 {@code v5ai-<32 位大小写字母与数字>}，与 tracking_id（独立 UUID，
     * 仅用于展示与审计）没有任何字符关系；运行时先按摘要命中 v5ai_api_keys.key_hash
     * 唯一索引定位密钥行，再做全串 BCrypt 校验。</p>
     *
     * @param apiKey 明文 ApiKey
     * @return 小写十六进制摘要；key 为空或前缀/长度不符合生成格式时返回 null
     */
    String keyHash(String apiKey);
}
