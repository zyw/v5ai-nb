package xin.v5ai.nb.api.apiKey;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xin.v5ai.nb.common.apiKey.ApplicationApiKeyVerifier;
import xin.v5ai.nb.common.platform.AppQuotaService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet variant of the runtime API key guard. The application runs on Servlet MVC
 * (Tomcat, pulled in transitively by Sa-Token), where WebFlux {@code WebFilter} beans
 * are not applied — without this filter the runtime endpoints would accept any request.
 *
 * 额外承担运行面限流与配额检查：验证通过后按 appKey 检查每分钟限流与每日配额，
 * 超限返回 429。
 */
public class ApplicationApiKeyServletFilter implements Filter {
    private static final String RUNTIME_PREFIX = "/api/v1/apps/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RATE_LIMITED_BODY = """
            {"success":false,"code":"CONFLICT","message":"rate limit exceeded or daily quota reached for this application"}
            """.strip();

    private final ApplicationApiKeyVerifier verifier;
    private final AppQuotaService quotaService;

    public ApplicationApiKeyServletFilter(ApplicationApiKeyVerifier verifier, AppQuotaService quotaService) {
        this.verifier = verifier;
        this.quotaService = quotaService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) servletRequest;
        var response = (HttpServletResponse) servletResponse;
        var path = request.getRequestURI();
        if (!path.startsWith(RUNTIME_PREFIX)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        var appKey = extractAppKey(path);
        var authorization = request.getHeader("Authorization");
        if (appKey == null || authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (apiKey.isEmpty() || !verifier.verify(appKey, apiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 限流 + 配额：配额/限流为 0 表示不限
        if (!quotaService.checkAllowed(appKey)) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(RATE_LIMITED_BODY);
            return;
        }
        chain.doFilter(servletRequest, servletResponse);
    }

    private String extractAppKey(String path) {
        var rest = path.substring(RUNTIME_PREFIX.length());
        var slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return rest.substring(0, slash);
    }
}
