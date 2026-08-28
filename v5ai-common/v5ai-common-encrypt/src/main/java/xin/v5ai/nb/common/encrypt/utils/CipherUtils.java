package xin.v5ai.nb.common.encrypt.utils;

import xin.v5ai.nb.common.core.domain.model.Tuple2;
import xin.v5ai.nb.common.core.utils.SpringUtils;
import xin.v5ai.nb.common.encrypt.apikey.AgentApiKeyVerifier;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

public class CipherUtils {

    public static String encrypt(String plaintext) {
        CredentialCipher cipher = SpringUtils.getBean(CredentialCipher.class);
        return cipher.encrypt(plaintext);
    }

    public static String decrypt(String ciphertext) {
        CredentialCipher cipher = SpringUtils.getBean(CredentialCipher.class);
        return cipher.decrypt(ciphertext);
    }

    /**
     * 验证ApiKey
     * @param secretHash 数据库中的密文
     * @param apiKey 接收的ApiKey
     * @return 是否验证通过
     */
    public static boolean verify(String secretHash, String apiKey) {
        return SpringUtils.getBean(AgentApiKeyVerifier.class).verify(secretHash, apiKey);
    }

    /**
     * 生成ApiKey
     * @return first: 生成的ApiKey second: 生成的ApiKey的密文
     */
    public static Tuple2<String, String> generateApiKey() {
        return SpringUtils.getBean(AgentApiKeyVerifier.class).generateApiKey();
    }
}
