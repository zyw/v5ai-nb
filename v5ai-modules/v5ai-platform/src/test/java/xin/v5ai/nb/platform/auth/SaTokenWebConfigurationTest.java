package xin.v5ai.nb.platform.auth;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaTokenWebConfigurationTest {

    private final SaTokenWebConfiguration configuration = new SaTokenWebConfiguration();

    @Test
    void asyncDispatchSkipsSaTokenCheck() throws Exception {
        var request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);

        // SSE 流式响应触发 ASYNC 分发时，跳过 Sa-Token 校验（异步线程无上下文），返回 true
        assertThat(configuration.adminAuthInterceptor()
                .preHandle(request, mock(HttpServletResponse.class), new Object()))
                .isTrue();
    }

    @Test
    void normalRequestStillRunsSaTokenCheck() throws Exception {
        var request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);

        // 首次 REQUEST 分发必须执行 Sa-Token 校验（未登录或上下文缺失都会抛异常）
        assertThatThrownBy(() -> configuration.adminAuthInterceptor()
                .preHandle(request, mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(Exception.class);
    }
}
