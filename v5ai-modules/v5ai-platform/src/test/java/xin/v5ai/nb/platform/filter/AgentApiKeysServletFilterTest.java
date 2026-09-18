package xin.v5ai.nb.platform.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.platform.api.ApiKeysService;
import xin.v5ai.nb.platform.api.AppQuotaService;
import xin.v5ai.nb.platform.api.domain.ApiKeysAuthDTO;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AgentApiKeysServletFilter} 的 Mockito 单测：把「多 Agent 绑定」引入的三态返回钉死。
 *
 * <p>401 = Key 缺失/无效/停用；403 = Key 有效但未绑定请求路径中的 Agent；429 = 限流或配额超限；
 * 三者都必须阻断过滤器链，只有全部通过才放行。</p>
 *
 * @author ZYW
 * @since 2026-09-25
 */
class AgentApiKeysServletFilterTest {

    private static final String KEY = "v5ai_abcdefghijklmnopqrstuvwxyz012345";
    private static final String PATH = "/api/v1/agents/a1/chat/stream";

    private AppQuotaService quotaService;
    private ApiKeysService apiKeyService;
    private AgentApiKeysServletFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter body;

    @BeforeEach
    void setUp() throws Exception {
        quotaService = mock(AppQuotaService.class);
        apiKeyService = mock(ApiKeysService.class);
        filter = new AgentApiKeysServletFilter(quotaService, apiKeyService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getRequestURI()).thenReturn(PATH);
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void unknownKeyIsUnauthorized() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void keyWithoutBindingForRequestedAgentIsForbidden() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a2")));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.toString()).contains("无权访问");
        verify(chain, never()).doFilter(any(), any());
        // 未绑定即拒绝，不应消耗配额/限流额度
        verifyNoInteractions(quotaService);
    }

    @Test
    void quotaExceededIsTooManyRequests() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));
        when(quotaService.checkAllowed("a1")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        assertThat(body.toString()).contains("CONFLICT");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void boundAndWithinQuotaPassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));
        when(quotaService.checkAllowed("a1")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void nonRuntimePathSkipsAuthentication() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/admin/api-keys");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(apiKeyService, quotaService);
        verify(request, never()).getHeader(anyString());
    }

    private ApiKeysAuthDTO auth(Set<String> agentKeys) {
        return new ApiKeysAuthDTO(1L, 7L, "生产环境", "abcdefghijkl", true, agentKeys);
    }
}