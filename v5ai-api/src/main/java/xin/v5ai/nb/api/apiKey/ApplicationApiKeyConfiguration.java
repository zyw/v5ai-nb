package xin.v5ai.nb.api.apiKey;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.apiKey.ApplicationApiKeyVerifier;

@Configuration
public class ApplicationApiKeyConfiguration {

    /**
     * WebFlux variant; active when the app runs on the reactive stack.
     */
    @Bean
    ApplicationApiKeyAuthenticationFilter applicationApiKeyAuthenticationFilter(ApplicationApiKeyVerifier verifier) {
        return new ApplicationApiKeyAuthenticationFilter(verifier);
    }

    /**
     * Servlet variant, registered for runtime paths only. The app runs on Servlet MVC
     * (Sa-Token pulls in spring-boot-starter-web), where WebFilter beans are ignored,
     * so this is the filter that actually enforces Application API Keys in production.
     */
    @Bean
    FilterRegistrationBean<ApplicationApiKeyServletFilter> applicationApiKeyServletFilter(
            ApplicationApiKeyVerifier verifier,
            xin.v5ai.nb.common.platform.AppQuotaService quotaService) {
        var registration = new FilterRegistrationBean<>(
                new ApplicationApiKeyServletFilter(verifier, quotaService));
        registration.addUrlPatterns("/api/v1/apps/*");
        registration.setName("applicationApiKeyServletFilter");
        return registration;
    }
}
