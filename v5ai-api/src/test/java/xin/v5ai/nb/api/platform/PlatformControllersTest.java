package xin.v5ai.nb.api.platform;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.common.platform.AppQuota;
import xin.v5ai.nb.common.platform.AppQuotaService;
import xin.v5ai.nb.common.platform.AuditLog;
import xin.v5ai.nb.common.platform.AuditRecorder;
import xin.v5ai.nb.common.platform.Tenant;
import xin.v5ai.nb.common.platform.TenantStore;
import xin.v5ai.nb.common.platform.UserAccount;
import xin.v5ai.nb.common.platform.UserAccountStore;
import xin.v5ai.nb.model.ModelRepository;
import xin.v5ai.nb.rag.KnowledgeBaseRepository;
import xin.v5ai.nb.skill.SkillRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformControllersTest {

    @Test
    void tenantControllerCreatesAndLists() {
        var store = new RecordingTenantStore();
        var controller = new TenantController(store, noOpAudit());
        var created = controller.createTenant(new TenantController.CreateTenantRequest("acme", "Acme Corp"));
        assertThat(created.tenantKey()).isEqualTo("acme");
        assertThat(controller.listTenants()).extracting(Tenant::tenantKey).containsExactly("acme");
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
                "bob", "secret123", "Bob", 1L, null));
        assertThat(created.username()).isEqualTo("bob");
        assertThat(created.roles()).containsExactly("USER");
        assertThat(controller.listUsers()).extracting(UserAccount::username).containsExactly("bob");
    }

    @Test
    void quotaControllerGetsAndUpdates() {
        var service = mock(AppQuotaService.class);
        when(service.getQuota("demo")).thenReturn(new AppQuota("demo", 0, 0, 0, null));
        when(service.updateQuota(anyString(), any(), any(), any()))
                .thenReturn(new AppQuota("demo", 10, 1000L, 5, Instant.now()));
        var controller = new AppQuotaController(service, noOpAudit());

        assertThat(controller.getQuota("demo").ratePerMinute()).isZero();
        var updated = controller.updateQuota("demo", new AppQuotaController.UpdateQuotaRequest(10, 1000L, 5));
        assertThat(updated.dailyModelCalls()).isEqualTo(10);
    }

    @Test
    void auditControllerSearches() {
        var recorder = mock(AuditRecorder.class);
        when(recorder.search("admin", "LOGIN", 100)).thenReturn(List.of(
                new AuditLog(1L, null, "admin", "LOGIN", "auth", "admin", "login", null, Instant.now())));
        var controller = new AuditController(recorder);
        assertThat(controller.search("admin", "LOGIN", 100)).hasSize(1);
    }

    @Test
    void statsControllerCountsResources() {
        var agents = mock(AgentRepository.class);
        var models = mock(ModelRepository.class);
        var knowledge = mock(KnowledgeBaseRepository.class);
        var skills = mock(SkillRepository.class);
        when(agents.list()).thenReturn(List.of(new xin.v5ai.nb.agent.Agent(
                "a", "A", null, xin.v5ai.nb.agent.AgentStatus.PUBLISHED, 1L, null)));
        when(models.list()).thenReturn(List.of(new xin.v5ai.nb.model.ModelConfig("p", "m", xin.v5ai.nb.model.ModelType.CHAT, "c")));
        when(knowledge.list()).thenReturn(List.of(new xin.v5ai.nb.rag.KnowledgeBase(1L, "kb", null, xin.v5ai.nb.rag.KnowledgeBaseStatus.ACTIVE)));
        when(skills.list()).thenReturn(List.of(new xin.v5ai.nb.skill.Skill(1L, "skill", "d", xin.v5ai.nb.skill.SkillStatus.ACTIVE, null, null, null, null)));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(3L);

        var overview = new StatsController(jdbc, agents, models, knowledge, skills).overview();

        assertThat(overview.agents()).isEqualTo(1);
        assertThat(overview.totalRuns()).isEqualTo(7);
        assertThat(overview.todayModelCalls()).isEqualTo(3);
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
            var account = new UserAccount(nextId++, username, displayName, tenantId, roles, true);
            stored.add(account);
            return account;
        }

        @Override
        public void updateRoles(Long userId, List<String> roles) {
        }

        @Override
        public void setEnabled(Long userId, boolean enabled) {
        }
    }
}
