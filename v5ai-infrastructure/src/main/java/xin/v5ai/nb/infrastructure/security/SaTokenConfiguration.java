package xin.v5ai.nb.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.model.CredentialCipher;

@Configuration
public class SaTokenConfiguration {
    @Bean
    PasswordVerifier passwordVerifier() {
        return new BCryptPasswordVerifier();
    }

    @Bean
    CredentialCipher credentialCipher() {
        return new AesCredentialCipher();
    }
}
