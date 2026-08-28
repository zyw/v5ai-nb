package xin.v5ai.nb.common.encrypt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import xin.v5ai.nb.common.encrypt.apikey.AgentApiKeyVerifier;
import xin.v5ai.nb.common.encrypt.apikey.BcryptAgentApiKeyVerifier;
import xin.v5ai.nb.common.encrypt.cipher.AesCredentialCipher;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

@AutoConfiguration
public class CipherAutoConfiguration {

    @Value("${v5ai.credential.key:tggsbeaGlYKF8JJ9eAaZixMkJIqaqhwe}")
    private String key;

    @Bean
    public CredentialCipher aesCredentialCipher() {
        return new AesCredentialCipher(key);
    }

    @Bean
    public AgentApiKeyVerifier agentApiKeyVerifier() {
        return new BcryptAgentApiKeyVerifier();
    }
}
