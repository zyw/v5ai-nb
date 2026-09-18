package xin.v5ai.nb.common.encrypt.apikeys;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import xin.v5ai.nb.common.core.domain.model.Tuple2;

import java.security.SecureRandom;

public class BcryptAgentApiKeysVerifier implements AgentApiKeysVerifier {

    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** 明文 Key 前缀 */
    private static final String KEY_PREFIX = "v5ai-";
    /** 明文 Key 随机主体长度（真正的秘密部分：62^32） */
    private static final int KEY_BODY_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 校验 API Key。
     *
     * @param secretHash 数据库中存储的密文（BCrypt）
     * @param apiKey     请求携带的明文 API Key
     * @return 是否匹配
     */
    @Override
    public boolean verify(String apiKey,String secretHash) {
        return secretHash != null && BCrypt.checkpw(apiKey, secretHash);
    }

    @Override
    public Tuple2<String, String> generateApiKey() {
        var rawKey = genApiKey();
        return Tuple2.<String, String>builder()
                .first(rawKey)
                .second(BCrypt.hashpw(rawKey))
                .build();
    }

    /**
     * SHA-256 摘要作为唯一索引上的定位手柄；前缀/长度不符的输入直接返回 null，不去查库。
     * 明文与 tracking_id 无关，所以只能靠摘要定位行。
     */
    @Override
    public String keyHash(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(KEY_PREFIX)
                || apiKey.length() != KEY_PREFIX.length() + KEY_BODY_LENGTH) {
            return null;
        }
        return DigestUtil.sha256Hex(apiKey);
    }

    /** 明文 Key = {@code v5ai-} + 32 位大小写字母与数字（唯一秘密，不含任何跟踪信息）。 */
    private String genApiKey() {
        var sb = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < KEY_BODY_LENGTH; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }
}
