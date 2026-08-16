package xin.v5ai.nb.api.mcp;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.mcp.AgentMcpBindingRepository;
import xin.v5ai.nb.mcp.CreateMcpServerCommand;
import xin.v5ai.nb.mcp.McpClientFactory;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpConnectionTestResult;
import xin.v5ai.nb.mcp.McpManagementService;
import xin.v5ai.nb.mcp.McpServer;
import xin.v5ai.nb.mcp.McpServerRepository;
import xin.v5ai.nb.mcp.McpServerStatus;
import xin.v5ai.nb.mcp.McpToolCallAudit;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolCallStatus;
import xin.v5ai.nb.mcp.McpToolDefinition;
import xin.v5ai.nb.mcp.McpToolInfo;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.McpToolRepository;
import xin.v5ai.nb.mcp.McpTransportType;
import xin.v5ai.nb.mcp.StdioCommandPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerControllerTest {
    @Test
    void createsAndListsServers() {
        var controller = new McpServerController(service());
        var created = controller.createServer(new CreateMcpServerRequest(
                "weather", McpTransportType.STREAMABLE_HTTP, "https://mcp.example.com/mcp",
                null, null, null, null));
        var listed = controller.listServers();

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.status()).isEqualTo(McpServerStatus.ACTIVE);
        assertThat(listed).extracting(McpServerResponse::name).containsExactly("weather");
    }

    @Test
    void testsConnectionAndDiscoversTools() {
        var controller = new McpServerController(service());
        controller.createServer(new CreateMcpServerRequest(
                "fs", McpTransportType.STDIO, "node", List.of("fs.js"), null, null, null));

        var test = controller.testConnection(1L);
        assertThat(test.ok()).isTrue();
        assertThat(test.toolCount()).isEqualTo(1);

        var tools = controller.discoverTools(1L);
        assertThat(tools).extracting(McpToolResponse::toolName).containsExactly("read_file");
        assertThat(tools.get(0).permission()).isEqualTo(McpToolPermission.ALLOW);
    }

    @Test
    void updatesToolPermission() {
        var controller = new McpServerController(service());
        controller.createServer(new CreateMcpServerRequest(
                "fs", McpTransportType.STDIO, "node", List.of("fs.js"), null, null, null));
        controller.discoverTools(1L);

        var updated = controller.updateToolPermission(1L, "read_file", new McpToolPermissionRequest(McpToolPermission.DENY));
        assertThat(updated.permission()).isEqualTo(McpToolPermission.DENY);
    }

    @Test
    void listsToolCallsByServer() {
        var audits = new RecordingAuditRepository();
        var service = new McpManagementService(
                new RecordingServerRepository(),
                new RecordingToolRepository(),
                new RecordingBindingRepository(),
                audits,
                new FakeClientFactory(),
                command -> {
                });
        var controller = new McpServerController(service);
        audits.stored.add(new McpToolCallAudit(null, "run-1", 1L, "s", "t", "{a=1}",
                McpToolPermission.ALLOW, McpToolCallStatus.SUCCESS, 5L, null, Instant.now()));

        var calls = controller.listToolCallsByServer(1L);
        assertThat(calls).extracting(McpToolCallAuditResponse::runId).containsExactly("run-1");
    }

    private static McpManagementService service() {
        return new McpManagementService(
                new RecordingServerRepository(),
                new RecordingToolRepository(),
                new RecordingBindingRepository(),
                new RecordingAuditRepository(),
                new FakeClientFactory(),
                command -> {
                });
    }

    private static final class RecordingServerRepository implements McpServerRepository {
        private final List<McpServer> stored = new ArrayList<>();

        @Override
        public McpServer save(McpServer server) {
            var withId = new McpServer(1L, server.name(), server.transportType(), server.endpoint(),
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
        }
    }

    private static final class RecordingToolRepository implements McpToolRepository {
        private final List<McpToolDefinition> stored = new ArrayList<>();

        @Override
        public McpToolDefinition upsert(McpToolDefinition tool) {
            var withId = new McpToolDefinition(10L, tool.serverId(), tool.toolName(), tool.description(),
                    tool.inputSchema(), tool.readOnly(), tool.permission(), Instant.now());
            stored.removeIf(t -> t.serverId().equals(tool.serverId()) && t.toolName().equals(tool.toolName()));
            stored.add(withId);
            return withId;
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
            if (existing != null) {
                stored.remove(existing);
                stored.add(existing.withPermission(permission));
            }
        }

        @Override
        public void deleteByServerId(Long serverId) {
        }

        @Override
        public void deleteByServerIdNotIn(Long serverId, List<String> keepToolNames) {
        }
    }

    private static final class RecordingBindingRepository implements AgentMcpBindingRepository {
        @Override
        public void save(String agentKey, List<Long> mcpServerIds) {
        }

        @Override
        public List<Long> findByAgentKey(String agentKey) {
            return List.of();
        }

        @Override
        public void delete(String agentKey) {
        }
    }

    private static final class RecordingAuditRepository implements McpToolCallAuditRepository {
        private final List<McpToolCallAudit> stored = new ArrayList<>();

        @Override
        public McpToolCallAudit save(McpToolCallAudit audit) {
            stored.add(audit);
            return audit;
        }

        @Override
        public List<McpToolCallAudit> findByRunId(String runId) {
            return stored;
        }

        @Override
        public List<McpToolCallAudit> findByServerId(Long serverId) {
            return stored;
        }
    }

    private static final class FakeClientFactory implements McpClientFactory {
        @Override
        public McpConnection connect(McpServer server) {
            return new FakeConnection();
        }
    }

    private static final class FakeConnection implements McpConnection {
        @Override
        public List<McpToolInfo> listTools() {
            return List.of(new McpToolInfo("read_file", "read a file",
                    Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))), true));
        }

        @Override
        public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
            return McpToolCallResult.success("ok");
        }

        @Override
        public void close() {
        }
    }
}
