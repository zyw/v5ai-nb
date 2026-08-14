package xin.v5ai.nb.api.apiKey;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApplicationApiKeyServletFilterTest {

    @Test
    void allowsRuntimeRequestWithValidAppKeyAndSecret() throws Exception {
        var filter = new ApplicationApiKeyServletFilter((appKey, apiKey) ->
                appKey.equals("demo") && apiKey.equals("secret"));
        var request = new MockHttpServletRequest("POST", "/api/v1/apps/demo/chat/stream");
        request.addHeader("Authorization", "Bearer secret");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsRuntimeRequestWithoutAuthorization() throws Exception {
        var filter = new ApplicationApiKeyServletFilter((appKey, apiKey) -> true);
        var request = new MockHttpServletRequest("POST", "/api/v1/apps/demo/chat/stream");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsRuntimeRequestWithInvalidApiKey() throws Exception {
        var filter = new ApplicationApiKeyServletFilter((appKey, apiKey) -> false);
        var request = new MockHttpServletRequest("POST", "/api/v1/apps/demo/chat/stream");
        request.addHeader("Authorization", "Bearer wrong");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void skipsNonRuntimePaths() throws Exception {
        var filter = new ApplicationApiKeyServletFilter((appKey, apiKey) -> true);
        var request = new MockHttpServletRequest("GET", "/api/admin/knowledge-bases");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
