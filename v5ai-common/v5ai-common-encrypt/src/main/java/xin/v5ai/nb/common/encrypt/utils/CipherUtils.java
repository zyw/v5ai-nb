package xin.v5ai.nb.common.encrypt.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.core.utils.SpringUtils;
import xin.v5ai.nb.common.encrypt.apikey.AgentApiKeyVerifier;
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
    private static final AgentApiKeyVerifier VERIFIER = SpringUtils.getBean(AgentApiKeyVerifier.class);
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
     * @param secretHash 数据库中的密文
     * @param apiKey 接收的ApiKey
     * @return 是否验证通过
     */
    public static boolean verify(String secretHash, String apiKey) {
        return VERIFIER.verify(secretHash, apiKey);
    }

    /**
     * 生成ApiKey
     * @return first: 生成的ApiKey second: 生成的ApiKey的密文
     */
    public static Tuple2<String, String> generateApiKey() {
        return VERIFIER.generateApiKey();
    }
}
