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

    private static final String KEY = "v5ai-abcdefghijklmnopqrstuvwxyz012345";
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

    /**
     * 门户初始化（/api/v1/agents/auth/bootstrap）只需要 Key 有效：它落在 /api/v1/agents/ 前缀下
     * 但不带 agentKey，不能被当成 agentKey=auth 去校验绑定；也不占 Agent 配额。
     */
    @Test
    void authBootstrapOnlyRequiresValidKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/auth/bootstrap");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(quotaService);
    }

    /** 门户平级资源（头像）仍要校验绑定，但不计入模型配额。 */
    @Test
    void authAvatarChecksBindingAndSkipsQuota() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/auth/a2/avatar");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(quotaService);
    }

    /** 已绑定的 Agent 头像：放行，且不消耗配额。 */
    @Test
    void authAvatarForBoundAgentPassesWithoutQuota() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/auth/a1/avatar");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verifyNoInteractions(quotaService);
    }

    /**
     * 回归：名字恰好叫 auth 的 Agent，其运行路径不能被 auth/ 前缀「吃掉」——
     * 只有 bootstrap 精确路径与 auth/{agentKey}/avatar 才算门户端点。
     */
    @Test
    void agentNamedAuthStillRunsAsAgentKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/auth/chat/stream");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("auth")));
        when(quotaService.checkAllowed("auth")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(quotaService).checkAllowed("auth");
    }

    /**
     * 门户附件上传（/api/v1/agents/resource/upload）不带 agentKey：只需 Key 有效，
     * 既不校验绑定、也不占 Agent 配额。
     */
    @Test
    void portalUploadOnlyRequiresValidKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/resource/upload");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(quotaService);
    }

    /** 门户附件读取（/api/v1/agents/attachments/{id}）同样只验 Key。 */
    @Test
    void portalAttachmentReadOnlyRequiresValidKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/attachments/42");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(quotaService);
    }

    /**
     * 回归：名字恰好叫 resource 的 Agent，其运行路径不能被「门户附件上传」吃掉——
     * 保留路径是精确的 /resource/upload，运行路径永远是 /{agentKey}/chat/...。
     */
    @Test
    void agentNamedResourceStillRunsAsAgentKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/resource/chat/stream");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("resource")));
        when(quotaService.checkAllowed("resource")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(quotaService).checkAllowed("resource");
    }

    /** 回归：名字恰好叫 attachments 的 Agent 同理（保留路径只匹配 /attachments/{纯数字}）。 */
    @Test
    void agentNamedAttachmentsStillRunsAsAgentKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/attachments/chat/stream");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("attachments")));
        when(quotaService.checkAllowed("attachments")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(quotaService).checkAllowed("attachments");
    }

    /** /attachments/ 后面不是资源 id（纯数字）时不是门户端点，仍按 agentKey 解析并校验绑定。 */
    @Test
    void attachmentsPathWithNonNumericIdFallsBackToAgentKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/attachments/x");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("a1")));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(quotaService);
    }

    /**
     * 回归：agentKey **以 auth 开头**的 Agent（如 authfoo）不能被 auth 保留族吃掉——
     * 保留族是完整的 {@code /api/v1/agents/auth/} 前缀，不是 {@code /api/v1/agents/auth} 的字符串前缀。
     */
    @Test
    void agentKeyStartingWithAuthStillRunsAsAgentKey() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/authfoo/chat/stream");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("authfoo")));
        when(quotaService.checkAllowed("authfoo")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(quotaService).checkAllowed("authfoo");
    }

    /**
     * 回归：门户会话列表与对话流共用同一套 agentKey 提取。
     * 前缀少一个结尾斜杠时，提取出的首段以 {@code /} 开头被判空，**所有**运行接口都变成 401。
     */
    @Test
    void conversationListIsGuardedLikeAnyOtherRuntimePath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agents/java/chat/conversations");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + KEY);
        when(apiKeyService.authenticate(KEY)).thenReturn(auth(Set.of("java")));
        when(quotaService.checkAllowed("java")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verify(quotaService).checkAllowed("java");
    }

    private ApiKeysAuthDTO auth(Set<String> agentKeys) {
        return new ApiKeysAuthDTO(1L, 7L, "生产环境", "abcdefghijkl", true, agentKeys);
    }
}