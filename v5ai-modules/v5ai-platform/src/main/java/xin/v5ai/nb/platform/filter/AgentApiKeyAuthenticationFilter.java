package xin.v5ai.nb.platform.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.encrypt.utils.CipherUtils;
import xin.v5ai.nb.platform.api.ApiKeyService;
import xin.v5ai.nb.platform.api.AppQuotaService;

import java.nio.charset.StandardCharsets;

/**
 * Agent API Key 鉴权过滤器（WebFlux 变体）。
 *
 * <p>守卫运行期接口 {@code /api/v1/agents/{agentKey}/...}：从请求头
 * {@code Authorization: Bearer <api-key>} 取出 API Key，将路径中的 agentKey
 * 作为密钥哈希与之一并交给 {@link CipherUtils#verify(String, String)}
 * （BCrypt 校验，见 {@code AgentApiKeyVerifier}）比对，缺失或不匹配返回 401。
 *
 * <p>注意：项目实际运行在 Servlet MVC 栈，WebFilter 类型的 Bean 不会被容器
 * 应用，此过滤器实际不生效；真正生效的是 {@link AgentApiKeyServletFilter}
 * （注册见 {@code AgentApiKeyConfiguration}）。
 */
@Deprecated // Replaced by AgentApiKeyServletFilter
public class AgentApiKeyAuthenticationFilter implements WebFilter {
    /** 运行期接口前缀：/api/v1/agents/{agentKey}/... */
    private static final String RUNTIME_PREFIX = "/api/v1/agents/";
    /** Authorization 请求头的 Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppQuotaService quotaService;
    private final ApiKeyService apiKeyService;

    public AgentApiKeyAuthenticationFilter(AppQuotaService quotaService, ApiKeyService apiKeyService) {
        this.quotaService = quotaService;
        this.apiKeyService = apiKeyService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication().value();
        // 非运行期路径不拦截，直接放行
        if (!path.startsWith(RUNTIME_PREFIX)) {
            return chain.filter(exchange);
        }

        // 从路径提取 agentKey，并要求携带 Bearer 形式的 API Key
        var agentKey = extractAgentKey(path);
        var authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (agentKey == null || authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        // 校验 API Key：与 agentKey 对应的密钥哈希比对，失败返回 401
        var apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (apiKey.isEmpty() || !apiKeyService.checkApiKey(agentKey, apiKey)) {
            return unauthorized(exchange);
        }

        // 限流 + 配额：配额/限流为 0 表示不限，超限返回 429
        if (!quotaService.checkAllowed(agentKey)) {
            return unauthorized(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 从运行期路径中提取 agentKey（第一个路径段）。
     * 形如 /api/v1/agents/{agentKey}/chat/stream → {agentKey}
     */
    private String extractAgentKey(String path) {
        var rest = path.substring(RUNTIME_PREFIX.length());
        var slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return rest.substring(0, slash);
    }

    /** 返回 401 Unauthorized 并结束响应 */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
