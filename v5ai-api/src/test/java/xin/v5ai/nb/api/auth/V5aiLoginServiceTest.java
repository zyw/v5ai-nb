package xin.v5ai.nb.api.auth;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.auth.UserCredentialStore;
import xin.v5ai.nb.common.core.platform.AuditLog;
import xin.v5ai.nb.common.core.platform.AuditRecorder;
import xin.v5ai.nb.common.core.platform.Menu;
import xin.v5ai.nb.common.core.platform.MenuStore;
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
                verifier, tokenIssuer, new RecordingAuditRecorder(), noOpMenuStore());

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
                new RecordingAuditRecorder(), noOpMenuStore());

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
                new RecordingAuditRecorder(), noOpMenuStore());

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void getRolePermissionReturnsUserRoleKeys() {
        var service = new V5aiLoginService(username -> "encoded",
                accountStore(new UserAccount(1L, "alice", null, null, List.of("ADMIN", "USER"), true)),
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
                new RecordingAuditRecorder(), noOpMenuStore());

        assertThat(service.getRolePermission(1L)).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(service.getRolePermission(999L)).isEmpty();
    }

    @Test
    void getMenuPermissionReturnsMenuCodesByRoles() {
        var menuStore = new MenuStore() {
            @Override
            public List<Menu> list() { return List.of(); }
            @Override
            public Menu findById(Long id) { return null; }
            @Override
            public Menu findByCode(String code) { return null; }
            @Override
            public Menu create(Long parentId, String code, String name, String type, String routeName,
                               String path, String icon, int sortOrder, boolean visible) { return null; }
            @Override
            public Menu update(Long id, Long parentId, String name, String type, String routeName,
                               String path, String icon, int sortOrder, boolean visible) { return null; }
            @Override
            public void delete(Long id) { }
            @Override
            public long countChildren(Long id) { return 0; }
            @Override
            public List<Long> listMenuIdsByRole(String roleKey) { return List.of(); }
            @Override
            public void replaceRoleMenus(String roleKey, List<Long> menuIds) { }
            @Override
            public List<Menu> listByRoleKeys(List<String> roleKeys) {
                if (!roleKeys.contains("ADMIN")) {
                    return List.of();
                }
                return List.of(
                        new Menu(1L, null, "dashboard", "总览", "MENU", "dashboard", "/dashboard", "dashboard", 1, true, null, null),
                        new Menu(2L, null, "agents", "Agent", "MENU", "agents", "/agents", "agents", 2, true, null, null));
            }
        };
        var service = new V5aiLoginService(username -> "encoded",
                accountStore(new UserAccount(1L, "alice", null, null, List.of("ADMIN"), true)),
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
                new RecordingAuditRecorder(), menuStore);

        assertThat(service.getMenuPermission(1L)).containsExactlyInAnyOrder("dashboard", "agents");
        assertThat(service.getMenuPermission(999L)).isEmpty();
    }

    private static MenuStore noOpMenuStore() {
        return new MenuStore() {
            @Override public List<Menu> list() { return List.of(); }
            @Override public Menu findById(Long id) { return null; }
            @Override public Menu findByCode(String code) { return null; }
            @Override public Menu create(Long parentId, String code, String name, String type, String routeName,
                                         String path, String icon, int sortOrder, boolean visible) { return null; }
            @Override public Menu update(Long id, Long parentId, String name, String type, String routeName,
                                         String path, String icon, int sortOrder, boolean visible) { return null; }
            @Override public void delete(Long id) { }
            @Override public long countChildren(Long id) { return 0; }
            @Override public List<Long> listMenuIdsByRole(String roleKey) { return List.of(); }
            @Override public void replaceRoleMenus(String roleKey, List<Long> menuIds) { }
            @Override public List<Menu> listByRoleKeys(List<String> roleKeys) { return List.of(); }
        };
    }

    private static UserAccountStore accountStore(UserAccount account) {
        return new UserAccountStore() {
            @Override
            public UserAccount findByUsername(String username) {
                return account;
            }

            @Override
            public UserAccount findById(Long id) {
                return id != null && id.equals(account.id()) ? account : null;
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
