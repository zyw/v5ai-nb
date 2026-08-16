package xin.v5ai.nb.api.platform;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.platform.AuditLog;
import xin.v5ai.nb.common.platform.AuditRecorder;
import xin.v5ai.nb.common.platform.Role;
import xin.v5ai.nb.common.platform.RoleStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleControllerTest {

    @Test
    void createsAndListsRoles() {
        var store = new RecordingRoleStore();
        var controller = new RoleController(store, noOpAudit());

        var created = controller.create(new RoleController.CreateRoleRequest("viewer", "Viewer", "只读查看"));

        assertThat(created.roleKey()).isEqualTo("VIEWER");
        assertThat(created.builtin()).isFalse();
        assertThat(controller.list()).extracting(Role::roleKey).contains("VIEWER");
    }

    @Test
    void createRejectsBlankAndInvalidKey() {
        var controller = new RoleController(new RecordingRoleStore(), noOpAudit());

        assertThatThrownBy(() -> controller.create(new RoleController.CreateRoleRequest("", "X", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.create(new RoleController.CreateRoleRequest("bad key", "X", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsDuplicate() {
        var store = new RecordingRoleStore();
        store.create("VIEWER", "Viewer", null);
        var controller = new RoleController(store, noOpAudit());

        assertThatThrownBy(() -> controller.create(new RoleController.CreateRoleRequest("viewer", "Viewer", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateRenamesRole() {
        var store = new RecordingRoleStore();
        store.create("VIEWER", "Viewer", "old");
        var controller = new RoleController(store, noOpAudit());

        var updated = controller.update("viewer", new RoleController.UpdateRoleRequest("Viewer Pro", "new"));

        assertThat(updated.name()).isEqualTo("Viewer Pro");
        assertThat(updated.description()).isEqualTo("new");
    }

    @Test
    void deleteRejectsBuiltinRole() {
        var store = new RecordingRoleStore();
        store.createBuiltin("ADMIN");
        var controller = new RoleController(store, noOpAudit());

        assertThatThrownBy(() -> controller.delete("ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("builtin");
    }

    @Test
    void deleteRejectsRoleInUse() {
        var store = new RecordingRoleStore();
        store.create("VIEWER", "Viewer", null);
        store.assignedUsers = 3;
        var controller = new RoleController(store, noOpAudit());

        assertThatThrownBy(() -> controller.delete("VIEWER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigned");
    }

    @Test
    void deleteRemovesUnusedRole() {
        var store = new RecordingRoleStore();
        store.create("VIEWER", "Viewer", null);
        var controller = new RoleController(store, noOpAudit());

        controller.delete("VIEWER");

        assertThat(store.list()).isEmpty();
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

    private static final class RecordingRoleStore implements RoleStore {
        private final List<Role> stored = new ArrayList<>();
        long assignedUsers = 0;

        @Override
        public List<Role> list() {
            return stored;
        }

        @Override
        public Role findByKey(String roleKey) {
            return stored.stream().filter(r -> r.roleKey().equals(roleKey)).findFirst().orElse(null);
        }

        @Override
        public Role create(String roleKey, String name, String description) {
            var role = new Role(roleKey, name, description, false, Instant.now(), Instant.now());
            stored.add(role);
            return role;
        }

        void createBuiltin(String roleKey) {
            stored.add(new Role(roleKey, roleKey, null, true, Instant.now(), Instant.now()));
        }

        @Override
        public Role update(String roleKey, String name, String description) {
            var existing = findByKey(roleKey);
            var updated = new Role(roleKey, name, description, existing.builtin(), existing.createdAt(), Instant.now());
            stored.set(stored.indexOf(existing), updated);
            return updated;
        }

        @Override
        public void delete(String roleKey) {
            stored.removeIf(r -> r.roleKey().equals(roleKey));
        }

        @Override
        public long countUsers(String roleKey) {
            return assignedUsers;
        }
    }
}
