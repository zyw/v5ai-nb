package xin.v5ai.nb.platform.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xin.v5ai.nb.common.core.platform.MenuStore;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.common.web.handler.GlobalExceptionHandler;
import xin.v5ai.nb.platform.service.impl.LoginServiceImpl;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final LoginServiceImpl loginService = mock(LoginServiceImpl.class);

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                        new AuthController(loginService, mock(UserAccountStore.class), mock(MenuStore.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginRejectsBlankUsername() throws Exception {
        mvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("用户名")));
    }

    @Test
    void loginRejectsBlankPassword() throws Exception {
        mvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("密码")));
    }

    @Test
    void loginAcceptsValidCredentials() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("Bearer", "token", 1L, "alice", "Alice", 1L, List.of("ADMIN")));

        mvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("token"));
    }
}
