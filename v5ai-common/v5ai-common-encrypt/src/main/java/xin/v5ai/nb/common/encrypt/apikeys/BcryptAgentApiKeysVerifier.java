package xin.v5ai.nb.common.encrypt.apikeys;

import cn.hutool.crypto.digest.BCrypt;
import xin.v5ai.nb.common.core.domain.model.Tuple2;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

public class BcryptAgentApiKeysVerifier implements AgentApiKeysVerifier {

    private static final String KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** 明文 Key 前缀 */
    private static final String KEY_PREFIX = "v5ai_";
    /** 明文 Key 随机主体长度（真正的秘密部分：62^32） */
    private static final int KEY_BODY_LENGTH = 32;
    /** tracking id 长度：明文 Key 内嵌的 UUID，定长 36（8-4-4-4-12） */
    private static final int TRACKING_ID_LENGTH = 36;
    /** tracking id 形态校验：形态不符的输入直接拒绝，不去查库 */
    private static final Pattern TRACKING_ID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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
                || apiKey.length() != KEY_PREFIX.length() + TRACKING_ID_LENGTH + KEY_BODY_LENGTH) {
            return null;
        }
        String trackingId = apiKey.substring(KEY_PREFIX.length(), KEY_PREFIX.length() + TRACKING_ID_LENGTH);
        return TRACKING_ID_PATTERN.matcher(trackingId).matches() ? trackingId : null;
    }

    /** 明文 Key = {@code v5ai_} + UUID（tracking id，明文入库用于定位）+ 32 位随机主体（秘密）。 */
    private String genApiKey() {
        var sb = new StringBuilder(KEY_PREFIX);
        sb.append(UUID.randomUUID());
        for (int i = 0; i < KEY_BODY_LENGTH; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }
}
