package xin.v5ai.nb.platform.apiKey;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.core.apiKey.AgentApiKeyVerifier;
import xin.v5ai.nb.common.core.platform.AppQuotaService;

@Configuration
public class AgentApiKeyConfiguration {

    /**
     * WebFlux variant; active when the app runs on the reactive stack.
     */
    @Bean
    AgentApiKeyAuthenticationFilter applicationApiKeyAuthenticationFilter(AgentApiKeyVerifier verifier) {
        return new AgentApiKeyAuthenticationFilter(verifier);
    }

    /**
     * Servlet variant, registered for runtime paths only. The app runs on Servlet MVC
     * (Sa-Token pulls in spring-boot-starter-web), where WebFilter beans are ignored,
     * so this is the filter that actually enforces Agent API Keys in production.
     */
    @Bean
    FilterRegistrationBean<AgentApiKeyServletFilter> applicationApiKeyServletFilter(
            AgentApiKeyVerifier verifier,
            AppQuotaService quotaService) {
        var registration = new FilterRegistrationBean<>(
                new AgentApiKeyServletFilter(verifier, quotaService));
        registration.addUrlPatterns("/api/v1/agents/*");
        registration.setName("applicationApiKeyServletFilter");
        return registration;
    }
}
