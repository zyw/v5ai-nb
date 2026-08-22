package xin.v5ai.nb.platform.controller;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.platform.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformControllersTest {

    @Test
    void tenantControllerCreatesAndLists() {
        var store = new RecordingTenantStore();
        var controller = new TenantController(store, noOpAudit());
        var created = controller.createTenant(new TenantController.CreateTenantRequest("acme", "Acme Corp")).getData();
        assertThat(created.tenantKey()).isEqualTo("acme");
        assertThat(controller.listTenants(new PageQuery()).getData().getRows())
                .extracting(Tenant::tenantKey).containsExactly("acme");
    }

    @Test
    void userControllerCreatesWithUserRole() {
        var store = new RecordingUserAccountStore();
        var controller = new UserController(store, new org.springframework.security.crypto.password.PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "hash";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        }, noOpAudit());
        var created = controller.createUser(new UserController.CreateUserRequest(
                "bob", "secret123", "Bob", 1L, null)).getData();
        assertThat(created.username()).isEqualTo("bob");
        assertThat(created.roles()).containsExactly("USER");
        assertThat(controller.listUsers(new PageQuery()).getData().getRows())
                .extracting(UserAccount::username).containsExactly("bob");
    }

    @Test
    void quotaControllerGetsAndUpdates() {
        var service = mock(AppQuotaService.class);
        when(service.getQuota("demo")).thenReturn(new AppQuota("demo", 0, 0, 0, null));
        when(service.updateQuota(anyString(), any(), any(), any()))
                .thenReturn(new AppQuota("demo", 10, 1000L, 5, Instant.now()));
        var controller = new AppQuotaController(service, noOpAudit());

        assertThat(controller.getQuota("demo").getData().ratePerMinute()).isZero();
        var updated = controller.updateQuota("demo", new AppQuotaController.UpdateQuotaRequest(10, 1000L, 5)).getData();
        assertThat(updated.dailyModelCalls()).isEqualTo(10);
    }

    @Test
    void auditControllerSearches() {
        var recorder = mock(AuditRecorder.class);
        when(recorder.search("admin", "LOGIN", 100)).thenReturn(List.of(
                new AuditLog(1L, null, "admin", "LOGIN", "auth", "admin", "login", null, Instant.now())));
        var controller = new AuditController(recorder);
        assertThat(controller.search(new PageQuery(), "admin", "LOGIN").getData().getRows()).hasSize(1);
    }

    private static AuditRecorder noOpAudit() {
        return new AuditRecorder() {
            @Override
            public void record(Long tenantId, String actor, String action, String targetType,
                               String targetId, String detail, String ip) {
            }

            @Override
            public List<AuditLog> search(String actor, String action, int limit) {
                return List.of();
            }
        };
    }

    private static final class RecordingTenantStore implements TenantStore {
        private final List<Tenant> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Tenant save(Tenant tenant) {
            var withId = new Tenant(nextId++, tenant.tenantKey(), tenant.name(), "ACTIVE",
                    Instant.now(), Instant.now());
            stored.add(withId);
            return withId;
        }

        @Override
        public Tenant findById(Long id) {
            return null;
        }

        @Override
        public Tenant findByKey(String tenantKey) {
            return stored.stream().filter(t -> t.tenantKey().equals(tenantKey)).findFirst().orElse(null);
        }

        @Override
        public List<Tenant> list() {
            return stored;
        }

        @Override
        public void disable(Long id) {
        }
    }

    private static final class RecordingUserAccountStore implements UserAccountStore {
        private final List<UserAccount> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public UserAccount findByUsername(String username) {
            return stored.stream().filter(u -> u.username().equals(username)).findFirst().orElse(null);
        }

        @Override
        public UserAccount findById(Long id) {
            return null;
        }

        @Override
        public List<UserAccount> list() {
            return stored;
        }

        @Override
        public UserAccount create(String username, String passwordHash, String displayName,
                                  Long tenantId, List<String> roles) {
            var account = new UserAccount(nextId++, username, displayName, tenantId, roles, "0");
            stored.add(account);
            return account;
        }

        @Override
        public void updateRoles(Long userId, List<String> roles) {
        }

        @Override
        public void setStatus(Long userId, String status) {
        }

        @Override
        public void updateLoginInfo(Long userId, String loginIp, java.time.Instant loginDate) {
        }
    }
}
