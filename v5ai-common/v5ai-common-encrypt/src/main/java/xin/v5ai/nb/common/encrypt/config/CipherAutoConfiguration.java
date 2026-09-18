package xin.v5ai.nb.common.encrypt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.encrypt.apikeys.AgentApiKeysVerifier;
import xin.v5ai.nb.common.encrypt.apikeys.BcryptAgentApiKeysVerifier;
import xin.v5ai.nb.common.encrypt.cipher.AesCredentialCipher;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.common.encrypt.cipher.Sm4CredentialCipher;

@Slf4j
@AutoConfiguration
public class CipherAutoConfiguration {

    /**
     * 密钥
     */
    @Value("${v5ai.crypto.secret-key:tggsbeaGlYKF8JJ9eAaZixMkJIqaqhwe}")
    private String secretKey;

    /**
     * AES 加密器
     *
     * @return AES 加密器
     */
    @Bean
    @ConditionalOnProperty(
            name = "v5ai.crypto.type",
            havingValue = "aes",
            matchIfMissing = true
    )
    public CredentialCipher aesCredentialCipher() {
        if (StringUtils.isBlank(secretKey)) {
            log.warn("Secret key is blank, using default value.");
        }
        return new AesCredentialCipher(secretKey);
    }

    /**
     * SM4 加密器
     *
     * @return SM4 加密器
     */
    @Bean
    @ConditionalOnProperty(
            name = "v5ai.crypto.type",
            havingValue = "sm4"
    )
    public CredentialCipher sm4CredentialCipher(
            /* 初始化向量 */
            @Value("${v5ai.crypto.iv}")
            String iv) {
        if (StringUtils.isBlank(secretKey)) {
            log.warn("Secret key is blank, using default value.");
        }
        if (StringUtils.isBlank(iv)) {
            log.warn("IV is blank, using default value.");
        }
        return new Sm4CredentialCipher(secretKey, iv);
    }

    /**
     * API 密钥验证器
     *
     * @return API 密钥验证器
     */
    @Bean
    public AgentApiKeysVerifier agentApiKeyVerifier() {
        return new BcryptAgentApiKeysVerifier();
    }
}
