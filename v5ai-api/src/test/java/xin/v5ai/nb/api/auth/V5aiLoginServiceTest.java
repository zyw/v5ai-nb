package xin.v5ai.nb.api.auth;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.auth.UserCredentialStore;
import xin.v5ai.nb.infrastructure.security.PasswordVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V5aiLoginServiceTest {
    @Test
    void validCredentialsReturnBearerToken() {
        UserCredentialStore store = username -> "encoded";
        PasswordVerifier verifier = (raw, encoded) -> raw.equals("secret") && encoded.equals("encoded");
        LoginTokenIssuer tokenIssuer = new LoginTokenIssuer() {
            @Override
            public String issue(String username) {
                return "jwt-token-for-" + username;
            }

            @Override
            public void logout() {
            }
        };
        var service = new V5aiLoginService(store, verifier, tokenIssuer);

        var response = service.login(new LoginRequest("alice", "secret"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void invalidCredentialsAreRejected() {
        var service = new V5aiLoginService(username -> "encoded", (raw, encoded) -> false,
                new LoginTokenIssuer() {
                    @Override
                    public String issue(String username) {
                        return "unused";
                    }

                    @Override
                    public void logout() {
                    }
                });

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "bad")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
