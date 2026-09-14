package xin.v5ai.nb.common.encrypt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import xin.v5ai.nb.common.encrypt.apikey.AgentApiKeyVerifier;
import xin.v5ai.nb.common.encrypt.apikey.BcryptAgentApiKeyVerifier;
import xin.v5ai.nb.common.encrypt.cipher.AesCredentialCipher;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.common.encrypt.cipher.Sm4CredentialCipher;

@AutoConfiguration
public class CipherAutoConfiguration {
    /**
     * 加密类型: aes 或 sm4
     */
    @Value("${v5ai.crypto.type:aes}")
    private String cryptoType;

    /**
     * 密钥
     */
    @Value("${v5ai.crypto.secret-key:tggsbeaGlYKF8JJ9eAaZixMkJIqaqhwe}")
    private String secretKey;

    /**
     * 初始化向量
     */
    @Value("${v5ai.crypto.iv}")
    private String iv;

    /**
     * AES 加密器
     *
     * @return AES 加密器
     */
    @Bean
    @ConditionalOnProperty(name = "v5ai.crypto.type", havingValue = "aes")
    public CredentialCipher aesCredentialCipher() {
        return new AesCredentialCipher(secretKey);
    }

    /**
     * SM4 加密器
     *
     * @return SM4 加密器
     */
    @Bean
    @ConditionalOnProperty(name = "v5ai.crypto.type", havingValue = "sm4")
    public CredentialCipher sm4CredentialCipher() {
        return new Sm4CredentialCipher(secretKey, iv);
    }

    /**
     * API 密钥验证器
     *
     * @return API 密钥验证器
     */
    @Bean
    public AgentApiKeyVerifier agentApiKeyVerifier() {
        return new BcryptAgentApiKeyVerifier();
    }
}
