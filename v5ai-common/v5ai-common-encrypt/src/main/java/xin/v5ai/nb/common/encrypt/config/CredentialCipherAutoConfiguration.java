package xin.v5ai.nb.common.encrypt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import xin.v5ai.nb.common.encrypt.core.AesCredentialCipher;
import xin.v5ai.nb.common.encrypt.core.CredentialCipher;

@AutoConfiguration
public class CredentialCipherAutoConfiguration {

    @Value("${v5ai.credential.key:tggsbeaGlYKF8JJ9eAaZixMkJIqaqhwe}")
    private String key;

    @Bean
    public CredentialCipher aesCredentialCipher() {
        return new AesCredentialCipher(key);
    }
}
