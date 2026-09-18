package xin.v5ai.nb.platform.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xin.v5ai.nb.platform.api.ApiKeysService;
import xin.v5ai.nb.platform.api.AppQuotaService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 运行期 API Key 鉴权过滤器（Servlet 版本）。
 *
 * <p>守卫 {@code /api/v1/agents/{agentKey}/...}：从 {@code Authorization: Bearer <api-key>}
 * 取明文 Key 交给 {@link ApiKeysService#authenticate(String)}——它按 Key 内嵌的 tracking id
 * 定位密钥行并做 BCrypt 校验，同时返回该 Key 可访问的 Agent 集合。</p>
 *
 * <p>处理结果：</p>
 * <ul>
 *   <li>401：缺少/格式错误的 Bearer、Key 不存在、已停用或密码不匹配；</li>
 *   <li>403：Key 有效但未绑定路径中的 agentKey（换 Key 无用，需在「API Key 管理」补绑定）；</li>
 *   <li>429：按 agentKey 的每分钟限流或每日配额超限；</li>
 *   <li>通过则继续过滤器链（Agent 是否已发布由运行时 PublishedAgentResolver 兜底判定）。</li>
 * </ul>
 */
public class AgentApiKeysServletFilter implements Filter {

    private static final String RUNTIME_PREFIX = "/api/v1/agents/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORBIDDEN_BODY = """
            {"code":403,"msg":"当前 API Key 无权访问该 Agent"}
            """.strip();
    private static final String RATE_LIMITED_BODY = """
            {"success":false,"code":"CONFLICT","message":"此Agent的速率限制已超或每日配额已满"}
            """.strip();

    private final AppQuotaService quotaService;
    private final ApiKeysService apiKeyService;

    public AgentApiKeysServletFilter(AppQuotaService quotaService, ApiKeysService apiKeyService) {
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

        // 校验 API Key：按 tracking id 定位密钥行 + BCrypt 校验，失败返回 401
        var apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        var auth = apiKey.isEmpty() ? null : apiKeyService.authenticate(apiKey);
        if (auth == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 密钥有效但未绑定路径中的 Agent：403（区别于 401，属于授权范围问题）
        if (!auth.allows(agentKey)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN_BODY);
            return;
        }

        // 限流 + 配额：配额/限流为 0 表示不限，超限返回 429
        if (!quotaService.checkAllowed(agentKey)) {
            writeJson(response, 429, RATE_LIMITED_BODY);
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

    /**
     * 输出 JSON 错误体（过滤器在 Spring MVC 之前执行，无法复用统一响应封装）。
     */
    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}