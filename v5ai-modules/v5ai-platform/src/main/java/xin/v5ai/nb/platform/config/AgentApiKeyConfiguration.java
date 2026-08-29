package xin.v5ai.nb.platform.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.platform.api.ApiKeyService;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.platform.filter.AgentApiKeyAuthenticationFilter;
import xin.v5ai.nb.platform.filter.AgentApiKeyServletFilter;

/**
 * Agent API Key 过滤器配置：为运行期接口（/api/v1/agents/*）注册鉴权过滤器。
 *
 * 同时声明 WebFlux 与 Servlet 两种变体：
 * - 项目实际运行在 Servlet MVC 栈（Sa-Token 引入 spring-boot-starter-web），
 *   WebFilter 类型的 Bean 不会被 Servlet 容器应用；
 * - 因此真正生效的是 {@link AgentApiKeyServletFilter}（Servlet 过滤器），
 *   并注入 {@link AppQuotaService} 在鉴权同时做应用配额/限流校验。
 */
@Configuration
public class AgentApiKeyConfiguration {

    /**
     * WebFlux 变体：仅当应用运行在响应式栈时生效（当前项目为 Servlet 栈，实际不生效）。
     */
    @Bean
    AgentApiKeyAuthenticationFilter applicationApiKeyAuthenticationFilter() {
        return new AgentApiKeyAuthenticationFilter();
    }

    /**
     * Servlet 变体：仅对运行期路径注册。项目运行在 Servlet MVC 栈，
     * 这是生产环境真正执行 Agent API Key 校验的过滤器。
     *
     * @param quotaService 应用配额/限流服务（鉴权后校验用量）
     */
    @Bean
    FilterRegistrationBean<AgentApiKeyServletFilter> applicationApiKeyServletFilter(AppQuotaService quotaService, ApiKeyService apiKeyService) {
        var registration = new FilterRegistrationBean<>(
                new AgentApiKeyServletFilter(quotaService, apiKeyService));
        registration.addUrlPatterns("/api/v1/agents/*");
        registration.setName("applicationApiKeyServletFilter");
        return registration;
    }
}
