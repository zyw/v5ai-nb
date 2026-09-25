package xin.v5ai.nb.platform.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.platform.api.ApiKeysService;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.platform.filter.AgentApiKeysServletFilter;

/**
 * Agent API Key 过滤器配置：为运行期接口与门户接口（都在 /api/v1/agents/** 下）注册鉴权过滤器。
 *
 * <p>项目运行在 Servlet MVC 栈，WebFilter 类型的 Bean 不会被 Servlet 容器应用，
 * 因此只注册 Servlet 过滤器 {@link AgentApiKeysServletFilter}，并注入
 * {@link AppQuotaService} 在鉴权同时做应用配额/限流校验。</p>
 */
@Configuration
public class AgentApiKeysConfiguration {

    /**
     * Servlet 变体：对 /api/v1/agents/** 注册，这是生产环境真正执行 API Key 校验的过滤器。
     * 注意：Servlet 过滤器按 URL pattern 生效，**新增其它前缀的运行期端点必须同步补注册**，否则完全绕过鉴权。
     *
     * @param quotaService  应用配额/限流服务（鉴权后按 agentKey 校验用量）
     * @param apiKeyService API Key 鉴权服务（定位密钥行 + 校验绑定）
     */
    @Bean
    FilterRegistrationBean<AgentApiKeysServletFilter> applicationApiKeyServletFilter(
            AppQuotaService quotaService, ApiKeysService apiKeyService) {
        var registration = new FilterRegistrationBean<>(
                new AgentApiKeysServletFilter(quotaService, apiKeyService));
        registration.addUrlPatterns("/api/v1/agents/*");
        registration.addUrlPatterns("/api/v1/workflows/*");
        registration.setName("applicationApiKeyServletFilter");
        return registration;
    }
}
