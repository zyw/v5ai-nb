package xin.v5ai.nb.platform.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xin.v5ai.nb.platform.api.ApiKeyService;
import xin.v5ai.nb.platform.api.AppQuotaService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 运行时API密钥保护的Servlet版本。该应用运行在Servlet MVC（通过Sa-Token间接引入的Tomcat）环境中，其中未应用WebFlux的{@code WebFilter} Bean——如果没有此过滤器，运行时端点将接受任何请求。
 *
 * 额外承担运行面限流与配额检查：验证通过后按 agentKey 检查每分钟限流与每日配额，
 * 超限返回 429。
 *
 * 处理结果：鉴权缺失/失败返回 401；限流或配额超限返回 429（JSON 错误体）；
 * 通过则继续过滤器链。
 */
public class AgentApiKeyServletFilter implements Filter {
    private static final String RUNTIME_PREFIX = "/api/v1/agents/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RATE_LIMITED_BODY = """
            {"success":false,"code":"CONFLICT","message":"此Agent的速率限制已超或每日配额已满"}
            """.strip();

    private final AppQuotaService quotaService;
    private final ApiKeyService apiKeyService;

    public AgentApiKeyServletFilter(AppQuotaService quotaService, ApiKeyService apiKeyService) {
        this.quotaService = quotaService;
        this.apiKeyService = apiKeyService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) servletRequest;
        var response = (HttpServletResponse) servletResponse;
        var path = request.getRequestURI();
        // 非运行期路径不拦截，直接放行
        if (!path.startsWith(RUNTIME_PREFIX)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 从路径提取 agentKey，并要求携带 Bearer 形式的 API Key
        var agentKey = extractAgentKey(path);
        var authorization = request.getHeader("Authorization");
        if (agentKey == null || authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 校验 API Key：与 agentKey 对应的密钥哈希比对，失败返回 401
        var apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (apiKey.isEmpty() || !apiKeyService.checkApiKey(agentKey, apiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 限流 + 配额：配额/限流为 0 表示不限，超限返回 429
        if (!quotaService.checkAllowed(agentKey)) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(RATE_LIMITED_BODY);
            return;
        }
        chain.doFilter(servletRequest, servletResponse);
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
}
