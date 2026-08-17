package xin.v5ai.nb.api.auth;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.auth.UserCredentialStore;
import xin.v5ai.nb.common.core.platform.AuditLog;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.core.platform.UserAccount;
import xin.v5ai.nb.common.core.platform.UserAccountStore;
import xin.v5ai.nb.infrastructure.security.PasswordVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V5aiLoginServiceTest {
    @Test
    void validCredentialsReturnBearerTokenWithAccountInfo() {
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
        var service = new V5aiLoginService(store, accountStore(
                new UserAccount(1L, "alice", "Alice", 1L, List.of("ADMIN"), true)),
                verifier, tokenIssuer, new RecordingAuditRecorder());

        var response = service.login(new LoginRequest("alice", "secret"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.token()).isNotBlank();
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.roles()).containsExactly("ADMIN");
    }

    @Test
    void invalidCredentialsAreRejected() {
        var service = new V5aiLoginService(username -> "encoded",
                accountStore(new UserAccount(1L, "alice", null, null, List.of("USER"), true)),
                (raw, encoded) -> false,
                new LoginTokenIssuer() {
                    @Override
                    public String issue(String username) {
                        return "unused";
                    }

                    @Override
                    public void logout() {
                    }
                },
                new RecordingAuditRecorder());

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "bad")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledAccountIsRejected() {
        var service = new V5aiLoginService(username -> "encoded",
                accountStore(new UserAccount(1L, "alice", null, null, List.of("USER"), false)),
                (raw, encoded) -> true,
                new LoginTokenIssuer() {
                    @Override
                    public String issue(String username) {
                        return "unused";
                    }

                    @Override
                    public void logout() {
                    }
                },
                new RecordingAuditRecorder());

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    private static UserAccountStore accountStore(UserAccount account) {
        return new UserAccountStore() {
            @Override
            public UserAccount findByUsername(String username) {
                return account;
            }

            @Override
            public UserAccount findById(Long id) {
                return null;
            }

            @Override
            public List<UserAccount> list() {
                return List.of();
            }

            @Override
            public UserAccount create(String username, String passwordHash, String displayName,
                                      Long tenantId, List<String> roles) {
                return null;
            }

            @Override
            public void updateRoles(Long userId, List<String> roles) {
            }

            @Override
            public void setEnabled(Long userId, boolean enabled) {
            }
        };
    }

    private static final class RecordingAuditRecorder implements AuditRecorder {
        @Override
        public void record(Long tenantId, String actor, String action,
                           String targetType, String targetId, String detail, String ip) {
        }

        @Override
        public List<AuditLog> search(String actor, String action, int limit) {
            return List.of();
        }
    }
}
