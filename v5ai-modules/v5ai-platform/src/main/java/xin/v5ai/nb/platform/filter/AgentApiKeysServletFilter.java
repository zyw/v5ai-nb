package xin.v5ai.nb.platform.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xin.v5ai.nb.platform.api.ApiKeyAuthAttributes;
import xin.v5ai.nb.platform.api.ApiKeysService;
import xin.v5ai.nb.platform.api.AppQuotaService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 运行期 API Key 鉴权过滤器（Servlet 版本）。
 *
 * <p>守卫 {@code /api/v1/agents/**}，从 {@code Authorization: Bearer <api-key>} 取明文 Key 交给
 * {@link ApiKeysService#authenticate(String)}（按明文 SHA-256 摘要定位密钥行 + BCrypt 校验），
 * 并把鉴权结果放进请求属性 {@link ApiKeyAuthAttributes#REQUEST_ATTRIBUTE} 供控制器复用：</p>
 * <ul>
 *   <li>{@code /api/v1/agents/auth/bootstrap}：门户初始化，只校验 Key；</li>
 *   <li>{@code /api/v1/agents/auth/{agentKey}/avatar}：门户平级资源，校验 Key + 绑定，不计配额；</li>
 *   <li>{@code /api/v1/agents/resource/upload} 与 {@code /api/v1/agents/attachments/{id}}：
 *       门户附件上传/读取，只校验 Key（不带 agentKey，故无绑定可校验、也不计模型配额）；</li>
 *   <li>{@code /api/v1/agents/{agentKey}/...}：运行路径，校验 Key + 该 Key 绑定 + 配额/限流。</li>
 * </ul>
 *
 * <p>处理结果：401=缺少/格式错误的 Bearer 或 Key 无效、已停用；403=Key 有效但未绑定路径中的 agentKey；
 * 429=按 agentKey 的每分钟限流或每日配额超限；通过则继续过滤器链。</p>
 */
public class AgentApiKeysServletFilter implements Filter {

    private static final String RUNTIME_PREFIX = "/api/v1/agents/";
    private static final String WORKFLOW_RUNTIME_PREFIX = "/api/v1/workflows/";
    /** 门户附件上传：位于 /api/v1/agents 前缀下但不带 agentKey，只验 Key */
    private static final String RESOURCE_UPLOAD_PATH = RUNTIME_PREFIX + "resource/upload";
    /** 门户附件读取前缀，其后应为纯数字资源 id */
    private static final String ATTACHMENTS_PREFIX = RUNTIME_PREFIX + "attachments/";

    /** 门户凭据平级资源（如 Agent 头像）的前缀与后缀：这些路径要校验绑定，但不消耗模型配额 */
    private static final String AUTH_PREFIX = RUNTIME_PREFIX + "auth/";
    /** 门户初始化：位于 /api/v1/agents 前缀下但不带 agentKey，只验 Key（不校验绑定、不计配额） */
    private static final String AUTH_BOOTSTRAP_PATH = AUTH_PREFIX + "bootstrap";

    private static final String AUTH_RESOURCE_SUFFIX = "/avatar";

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
        boolean bootstrap = AUTH_BOOTSTRAP_PATH.equals(path);
        // 注意：/api/v1/agents/auth/{agentKey}/avatar 的第一段是 auth 而不是 agentKey，
        // 只按「auth 前缀 + /avatar 后缀」识别，这样叫 auth 的 Agent 的运行路径仍按 agentKey=auth 正常解析。
        boolean authResource = path.startsWith(AUTH_PREFIX) && path.endsWith(AUTH_RESOURCE_SUFFIX);
        boolean portalResource = isPortalResourcePath(path);
        boolean runtime = !bootstrap && !portalResource && path.startsWith(RUNTIME_PREFIX);
        boolean workflowRuntime = path.startsWith(WORKFLOW_RUNTIME_PREFIX);
        // 非运行期路径不拦截，直接放行
        if (!runtime && !workflowRuntime && !bootstrap && !portalResource) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 要求携带 Bearer 形式的 API Key
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        var apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        var auth = apiKey.isEmpty() ? null : apiKeyService.authenticate(apiKey);
        if (auth == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(ApiKeyAuthAttributes.REQUEST_ATTRIBUTE, auth);

        // Workflow controller authorizes and charges every Agent referenced by the published definition.
        if (workflowRuntime) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 门户初始化与门户附件端点只要求 Key 有效：没有 agentKey，也就没有绑定与配额
        if (bootstrap || portalResource) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 取 agentKey：auth 平级资源取 auth/ 之后的段，其余运行路径取 agents/ 之后的段
        String agentKey = authResource
                ? extractAgentKey(path, AUTH_PREFIX)
                : extractAgentKey(path, RUNTIME_PREFIX);
        if (agentKey == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        // Key 有效但未绑定路径中的 Agent：403（区别于 401，属于授权范围问题）
        if (!auth.allows(agentKey)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN_BODY);
            return;
        }
        // 限流 + 配额只作用于按 Agent 运行的路径（读头像等平级资源不计入模型配额）
        if (!authResource && !quotaService.checkAllowed(agentKey)) {
            writeJson(response, 429, RATE_LIMITED_BODY);
            return;
        }
        chain.doFilter(servletRequest, servletResponse);
    }

    /**
     * 是否为门户附件端点（上传 / 读取）：这两个族不带 agentKey，因此只验 Key。
     *
     * <p>按**精确形状**识别而不是按前缀，避免把名字恰好叫 {@code resource} / {@code attachments}
     * 的 Agent「吃掉」：运行路径永远是 {@code /{agentKey}/chat/...}，与
     * {@code /resource/upload}、{@code /attachments/{纯数字id}} 的形状不可能重合。</p>
     */
    private boolean isPortalResourcePath(String path) {
        if (RESOURCE_UPLOAD_PATH.equals(path)) {
            return true;
        }
        if (!path.startsWith(ATTACHMENTS_PREFIX)) {
            return false;
        }
        var resourceId = path.substring(ATTACHMENTS_PREFIX.length());
        return !resourceId.isEmpty() && resourceId.chars().allMatch(Character::isDigit);
    }

    /**
     * 从受守卫路径中提取 agentKey（前缀后的第一个路径段）。
     * 形如 /api/v1/agents/{agentKey}/chat/stream 或 /api/v1/agents/auth/{agentKey}/avatar
     *
     * <p>前缀的结尾斜杠可有可无（历史上两种写法都出现过）。这里统一容忍：
     * 少一个斜杠只会让提取失败、进而让**所有**运行接口 401，属于「静默全站不可用」级别的坑，
     * 不值得依赖调用方拼对那一个字符。</p>
     *
     * @param path   请求路径
     * @param prefix 该族路径的前缀（结尾斜杠可有可无）
     * @return agentKey；路径结构不符合预期时返回 null
     */
    private String extractAgentKey(String path, String prefix) {
        var rest = path.substring(prefix.length());
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
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
