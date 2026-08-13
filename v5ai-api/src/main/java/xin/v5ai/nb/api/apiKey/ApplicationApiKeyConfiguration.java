package xin.v5ai.nb.api.apiKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.apiKey.ApplicationApiKeyVerifier;

@Configuration
public class ApplicationApiKeyConfiguration {
    @Bean
    ApplicationApiKeyAuthenticationFilter applicationApiKeyAuthenticationFilter(ApplicationApiKeyVerifier verifier) {
        return new ApplicationApiKeyAuthenticationFilter(verifier);
    }
}
