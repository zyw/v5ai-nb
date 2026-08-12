package xin.v5ai.nb.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfiguration {
    @Bean
    PasswordVerifier passwordVerifier() {
        return new BCryptPasswordVerifier();
    }
}
