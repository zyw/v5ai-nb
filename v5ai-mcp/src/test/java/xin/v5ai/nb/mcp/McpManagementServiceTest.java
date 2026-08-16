package xin.v5ai.nb.mcp;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpManagementServiceTest {

    @Test
    void createsServerWithDefaults() {
        var service = service(new AllowingStdioPolicy());
        var created = service.createServer(new CreateMcpServerCommand(
                "weather", McpTransportType.STREAMABLE_HTTP, "https://mcp.example.com/mcp",
                null, Map.of("Authorization", "Bearer x"), null, null));

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.timeoutSeconds()).isEqualTo(30);
        assertThat(created.status()).isEqualTo(McpServerStatus.ACTIVE);
        assertThat(created.headers()).containsEntry("Authorization", "Bearer x");
    }

    @Test
    void createServerRejectsMissingFields() {
        var service = service(new AllowingStdioPolicy());
        assertThatThrownBy(() -> service.createServer(new CreateMcpServerCommand(
                null, McpTransportType.SSE, "https://x", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> service.createServer(new CreateMcpServerCommand(
                "x", McpTransportType.SSE, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void createStdioServerEnforcesCommandPolicy() {
        var service = service(new RejectingStdioPolicy());
        assertThatThrownBy(() -> service.createServer(new CreateMcpServerCommand(
                "git", McpTransportType.STDIO, "python", List.of("-m", "git_server"), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void disableAndListServers() {
        var service = service(new AllowingStdioPolicy());
        service.createServer(new CreateMcpServerCommand("a", McpTransportType.SSE, "https://a", null, null, null, null));
        service.createServer(new CreateMcpServerCommand("b", McpTransportType.SSE, "https://b", null, null, null, null));
        service.disableServer(1L);
        assertThat(service.listServers()).extracting(McpServer::status)
                .containsExactly(McpServerStatus.DISABLED, McpServerStatus.ACTIVE);
    }

    @Test
    void bindServersRequiresActiveServer() {
        var service = service(new AllowingStdioPolicy());
        service.createServer(new CreateMcpServerCommand("a", McpTransportType.SSE, "https://a", null, null, null, null));
        service.disableServer(1L);
        assertThatThrownBy(() -> service.bindServers("demo-app", List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
        assertThatThrownBy(() -> service.bindServers("demo-app", List.of(99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void bindAndGetServerBindings() {
        var service = service(new AllowingStdioPolicy());
        service.createServer(new CreateMcpServerCommand("a", McpTransportType.SSE, "https://a", null, null, null, null));
        service.bindServers("demo-app", List.of(1L));
        assertThat(service.getServerBindings("demo-app")).containsExactly(1L);
    }

    @Test
    void testConnectionSuccessAndFailure() {
        var service = service(new AllowingStdioPolicy(), new FakeClientFactory(true, List.of(
                new McpToolInfo("get_weather", "weather", Map.of("type", "object"), true))));
        service.createServer(new CreateMcpServerCommand("a", McpTransportType.SSE, "https://a", null, null, null, null));

        var ok = service.testConnection(1L);
        assertThat(ok.ok()).isTrue();
        assertThat(ok.toolCount()).isEqualTo(1);
        assertThat(service.getServer(1L).lastTestStatus()).isEqualTo("ok");

        var failingService = service(new AllowingStdioPolicy(), new FakeClientFactory(false, List.of()));
        failingService.createServer(new CreateMcpServerCommand("b", McpTransportType.SSE, "https://b", null, null, null, null));
        var failed = failingService.testConnection(1L);
        assertThat(failed.ok()).isFalse();
        assertThat(failingService.getServer(1L).lastTestStatus()).isEqualTo("failed");
    }

    @Test
    void discoverToolsUpsertsWithDefaultPermissionAndPreservesCustomPermission() {
        var service = service(new AllowingStdioPolicy(), new FakeClientFactory(true, List.of(
                new McpToolInfo("read_file", "read", Map.of("type", "object"), true),
                new McpToolInfo("write_file", "write", Map.of("type", "object"), false))));
        service.createServer(new CreateMcpServerCommand("fs", McpTransportType.STDIO, "node", List.of("fs.js"), null, null, null));

        var discovered = service.discoverTools(1L);
        assertThat(discovered).extracting(McpToolDefinition::toolName).containsExactly("read_file", "write_file");
        assertThat(service.listTools(1L).get(0).permission()).isEqualTo(McpToolPermission.ALLOW); // readOnly → ALLOW
        assertThat(service.listTools(1L).get(1).permission()).isEqualTo(McpToolPermission.APPROVE); // writable → APPROVE

        // 管理员改为 DENY，再次发现应保留
        service.updateToolPermission(1L, "read_file", McpToolPermission.DENY);
        service.discoverTools(1L);
        assertThat(service.listTools(1L).get(0).permission()).isEqualTo(McpToolPermission.DENY);
        assertThat(service.listTools(1L).get(1).permission()).isEqualTo(McpToolPermission.APPROVE);
    }

    @Test
    void discoverToolsRemovesStaleTools() {
        var factory = new FakeClientFactory(true, List.of(
                new McpToolInfo("tool_a", "a", Map.of("type", "object"), true)));
        var service = service(new AllowingStdioPolicy(), factory);
        service.createServer(new CreateMcpServerCommand("s", McpTransportType.SSE, "https://s", null, null, null, null));
        service.discoverTools(1L);
        // 服务器端下线 tool_a，新增 tool_b
        factory.tools = List.of(new McpToolInfo("tool_b", "b", Map.of("type", "object"), true));
        service.discoverTools(1L);
        assertThat(service.listTools(1L)).extracting(McpToolDefinition::toolName).containsExactly("tool_b");
    }

    @Test
    void updateToolPermissionValidatesTool() {
        var service = service(new AllowingStdioPolicy(), new FakeClientFactory(true, List.of(
                new McpToolInfo("tool_a", "a", Map.of("type", "object"), true))));
        service.createServer(new CreateMcpServerCommand("s", McpTransportType.SSE, "https://s", null, null, null, null));
        service.discoverTools(1L);
        assertThatThrownBy(() -> service.updateToolPermission(1L, "missing_tool", McpToolPermission.ALLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void auditQueries() {
        var audits = new InMemoryAuditRepository();
        var service = new McpManagementService(
                new InMemoryServerRepository(),
                new InMemoryToolRepository(),
                new InMemoryBindingRepository(),
                audits,
                new FakeClientFactory(true, List.of()),
                new AllowingStdioPolicy());
        audits.save(new McpToolCallAudit(null, "run-1", 1L, "s", "t", "{a=1}",
                McpToolPermission.ALLOW, McpToolCallStatus.SUCCESS, 12L, null, Instant.now()));
        assertThat(service.listToolCallsByRun("run-1")).hasSize(1);
        assertThat(service.listToolCallsByServer(1L)).hasSize(1);
    }

    private static McpManagementService service(StdioCommandPolicy policy) {
        return service(policy, new FakeClientFactory(true, List.of()));
    }

    private static McpManagementService service(StdioCommandPolicy policy, McpClientFactory factory) {
        return new McpManagementService(
                new InMemoryServerRepository(),
                new InMemoryToolRepository(),
                new InMemoryBindingRepository(),
                new InMemoryAuditRepository(),
                factory,
                policy);
    }

    private static final class AllowingStdioPolicy implements StdioCommandPolicy {
        @Override
        public void validate(String command) {
        }
    }

    private static final class RejectingStdioPolicy implements StdioCommandPolicy {
        @Override
        public void validate(String command) {
            throw new IllegalArgumentException("stdio command is not allowed: " + command);
        }
    }

    private static final class FakeClientFactory implements McpClientFactory {
        private final boolean available;
        private List<McpToolInfo> tools;

        private FakeClientFactory(boolean available, List<McpToolInfo> tools) {
            this.available = available;
            this.tools = tools;
        }

        @Override
        public McpConnection connect(McpServer server) {
            if (!available) {
                throw new IllegalStateException("server unreachable");
            }
            return new FakeConnection(tools);
        }
    }

    private static final class FakeConnection implements McpConnection {
        private final List<McpToolInfo> tools;

        private FakeConnection(List<McpToolInfo> tools) {
            this.tools = tools;
        }

        @Override
        public List<McpToolInfo> listTools() {
            return tools;
        }

        @Override
        public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
            return McpToolCallResult.success("ok");
        }

        @Override
        public void close() {
        }
    }

    private static final class InMemoryServerRepository implements McpServerRepository {
        private final List<McpServer> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public McpServer save(McpServer server) {
            var withId = new McpServer(nextId++, server.name(), server.transportType(), server.endpoint(),
                    server.args(), server.headers(), server.envVars(), server.timeoutSeconds(),
                    server.status(), server.lastTestStatus(), server.lastTestMessage(), server.lastTestedAt());
            stored.add(withId);
            return withId;
        }

        @Override
        public McpServer update(McpServer server) {
            for (int i = 0; i < stored.size(); i++) {
                if (stored.get(i).id().equals(server.id())) {
                    stored.set(i, server);
                    return server;
                }
            }
            return server;
        }

        @Override
        public McpServer findById(Long id) {
            return stored.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<McpServer> list() {
            return stored;
        }

        @Override
        public void disable(Long id) {
            update(findById(id).withLastTest(null, null, null)
                    .withStatus(McpServerStatus.DISABLED));
        }
    }

    private static final class InMemoryToolRepository implements McpToolRepository {
        private final List<McpToolDefinition> stored = new ArrayList<>();
        private long nextId = 1;

        @Override
        public McpToolDefinition upsert(McpToolDefinition tool) {
            var existing = findByServerAndName(tool.serverId(), tool.toolName());
            if (existing == null) {
                var withId = new McpToolDefinition(nextId++, tool.serverId(), tool.toolName(),
                        tool.description(), tool.inputSchema(), tool.readOnly(), tool.permission(), Instant.now());
                stored.add(withId);
                return withId;
            }
            var updated = new McpToolDefinition(existing.id(), tool.serverId(), tool.toolName(),
                    tool.description(), tool.inputSchema(), tool.readOnly(), existing.permission(), Instant.now());
            stored.remove(existing);
            stored.add(updated);
            return updated;
        }

        @Override
        public List<McpToolDefinition> findByServerId(Long serverId) {
            return stored.stream().filter(t -> t.serverId().equals(serverId)).toList();
        }

        @Override
        public McpToolDefinition findByServerAndName(Long serverId, String toolName) {
            return stored.stream()
                    .filter(t -> t.serverId().equals(serverId) && t.toolName().equals(toolName))
                    .findFirst().orElse(null);
        }

        @Override
        public void updatePermission(Long serverId, String toolName, McpToolPermission permission) {
            var existing = findByServerAndName(serverId, toolName);
            if (existing == null) {
                return;
            }
            stored.remove(existing);
            stored.add(existing.withPermission(permission));
        }

        @Override
        public void deleteByServerId(Long serverId) {
            stored.removeIf(t -> t.serverId().equals(serverId));
        }

        @Override
        public void deleteByServerIdNotIn(Long serverId, List<String> keepToolNames) {
            stored.removeIf(t -> t.serverId().equals(serverId) && !keepToolNames.contains(t.toolName()));
        }
    }

    private static final class InMemoryBindingRepository implements AgentMcpBindingRepository {
        private final Map<String, List<Long>> bindings = new HashMap<>();

        @Override
        public void save(String agentKey, List<Long> mcpServerIds) {
            bindings.put(agentKey, new ArrayList<>(mcpServerIds));
        }

        @Override
        public List<Long> findByAgentKey(String agentKey) {
            return bindings.getOrDefault(agentKey, List.of());
        }

        @Override
        public void delete(String agentKey) {
            bindings.remove(agentKey);
        }
    }

    private static final class InMemoryAuditRepository implements McpToolCallAuditRepository {
        private final List<McpToolCallAudit> stored = new ArrayList<>();

        @Override
        public McpToolCallAudit save(McpToolCallAudit audit) {
            stored.add(audit);
            return audit;
        }

        @Override
        public List<McpToolCallAudit> findByRunId(String runId) {
            return stored.stream().filter(a -> runId.equals(a.runId())).toList();
        }

        @Override
        public List<McpToolCallAudit> findByServerId(Long serverId) {
            return stored.stream().filter(a -> serverId.equals(a.serverId())).toList();
        }
    }
}
