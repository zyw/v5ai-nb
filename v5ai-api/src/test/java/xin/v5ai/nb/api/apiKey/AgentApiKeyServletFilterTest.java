package xin.v5ai.nb.api.apiKey;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import xin.v5ai.nb.common.core.platform.AppQuota;
import xin.v5ai.nb.common.core.platform.AppQuotaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentApiKeyServletFilterTest {

    private static AppQuotaService quotaService(boolean allowed) {
        var service = mock(AppQuotaService.class);
        when(service.checkAllowed("demo")).thenReturn(allowed);
        when(service.getQuota("demo")).thenReturn(new AppQuota("demo", 0, 0, 0, null));
        return service;
    }

    @Test
    void allowsRuntimeRequestWithValidAgentKeyAndSecret() throws Exception {
        var filter = new AgentApiKeyServletFilter((agentKey, apiKey) ->
                agentKey.equals("demo") && apiKey.equals("secret"), quotaService(true));
        var request = new MockHttpServletRequest("POST", "/api/v1/agents/demo/chat/stream");
        request.addHeader("Authorization", "Bearer secret");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsRuntimeRequestWithoutAuthorization() throws Exception {
        var filter = new AgentApiKeyServletFilter((agentKey, apiKey) -> true, quotaService(true));
        var request = new MockHttpServletRequest("POST", "/api/v1/agents/demo/chat/stream");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsRuntimeRequestWithInvalidApiKey() throws Exception {
        var filter = new AgentApiKeyServletFilter((agentKey, apiKey) -> false, quotaService(true));
        var request = new MockHttpServletRequest("POST", "/api/v1/agents/demo/chat/stream");
        request.addHeader("Authorization", "Bearer wrong");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsRequestWhenQuotaOrRateLimitExceeded() throws Exception {
        var filter = new AgentApiKeyServletFilter((agentKey, apiKey) -> true, quotaService(false));
        var request = new MockHttpServletRequest("POST", "/api/v1/agents/demo/chat/stream");
        request.addHeader("Authorization", "Bearer secret");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void skipsNonRuntimePaths() throws Exception {
        var filter = new AgentApiKeyServletFilter((agentKey, apiKey) -> true, quotaService(true));
        var request = new MockHttpServletRequest("GET", "/api/admin/knowledge-bases");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
