package xin.v5ai.nb.common.encrypt.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.core.utils.SpringUtils;
import xin.v5ai.nb.common.encrypt.apikeys.AgentApiKeysVerifier;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

/**
 * 加密工具类
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CipherUtils {

    /**
     * 密码加密工具类
     */
    private static final CredentialCipher CIPHER = SpringUtils.getBean(CredentialCipher.class);
    /**
     * ApiKey验证工具类
     */
    private static final AgentApiKeysVerifier VERIFIER = SpringUtils.getBean(AgentApiKeysVerifier.class);
    /**
     * 加密明文密码
     * @param plaintext 明文密码
     * @return 密文密码
     */
    public static String encrypt(String plaintext) {
        return CIPHER.encrypt(plaintext);
    }

    /**
     * 解密密文密码
     * @param ciphertext 密文密码
     * @return 明文密码
     */
    public static String decrypt(String ciphertext) {
        return CIPHER.decrypt(ciphertext);
    }

    /**
     * 验证ApiKey
     * @param apiKey 接收的ApiKey
     * @param secretHash 数据库中的密文
     * @return 是否验证通过
     */
    public static boolean verify(String apiKey, String secretHash) {
        return VERIFIER.verify(apiKey, secretHash);
    }

    /**
     * 生成ApiKey
     * @return first: 生成的ApiKey second: 生成的ApiKey的密文
     */
    public static Tuple2<String, String> generateApiKey() {
        return VERIFIER.generateApiKey();
    }

    /**
     * 取明文 ApiKey 的 SHA-256 摘要，用于在唯一索引上定位密钥行（明文与 tracking_id 无关）。
     *
     * @param apiKey 明文 ApiKey
     * @return 小写十六进制摘要；格式不符时返回 null
     */
    public static String keyHash(String apiKey) {
        return VERIFIER.keyHash(apiKey);
    }
}
