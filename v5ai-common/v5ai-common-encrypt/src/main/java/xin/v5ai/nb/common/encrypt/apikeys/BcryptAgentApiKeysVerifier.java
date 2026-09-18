package xin.v5ai.nb.common.encrypt.apikeys;

import cn.hutool.crypto.digest.BCrypt;
import xin.v5ai.nb.common.core.domain.model.Tuple2;

import java.security.SecureRandom;

public class BcryptAgentApiKeysVerifier implements AgentApiKeysVerifier {

    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** 明文 Key 前缀 */
    private static final String KEY_PREFIX = "v5ai_";
    /** 明文 Key 随机主体长度 */
    private static final int KEY_BODY_LENGTH = 32;
    /** tracking id 长度：取随机主体前 12 位作为定位手柄（62^12 空间，配合唯一索引足够） */
    private static final int TRACKING_ID_LENGTH = 12;
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

    @Override
    public String trackingIdOf(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(KEY_PREFIX)
                || apiKey.length() != KEY_PREFIX.length() + KEY_BODY_LENGTH) {
            return null;
        }
        return apiKey.substring(KEY_PREFIX.length(), KEY_PREFIX.length() + TRACKING_ID_LENGTH);
    }

    private String genApiKey() {
        var sb = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < KEY_BODY_LENGTH; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }
}
