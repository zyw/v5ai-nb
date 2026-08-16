package xin.v5ai.nb.infrastructure.mcp;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.mcp.AgentMcpBindingRepository;
import xin.v5ai.nb.mcp.McpClientFactory;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpServer;
import xin.v5ai.nb.mcp.McpServerRepository;
import xin.v5ai.nb.mcp.McpServerStatus;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolDefinition;
import xin.v5ai.nb.mcp.McpToolInfo;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.McpToolRepository;
import xin.v5ai.nb.mcp.McpTransportType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMcpToolResolverTest {

    @Test
    void resolvesNothingWhenNoBindings() {
        var resolver = new AgentMcpToolResolver(
                new RecordingBindingRepository(List.of()),
                new RecordingServerRepository(),
                new RecordingToolRepository(),
                new FakeClientFactory());
        assertThat(resolver.resolveTools(app())).isEmpty();
    }

    @Test
    void resolvesToolsAndFiltersDenied() {
        var resolver = new AgentMcpToolResolver(
                new RecordingBindingRepository(List.of(1L)),
                new RecordingServerRepository(server(1L, "fs")),
                new RecordingToolRepository(List.of(
                        tool(1L, "read_file", McpToolPermission.ALLOW),
                        tool(1L, "write_file", McpToolPermission.APPROVE),
                        tool(1L, "delete_file", McpToolPermission.DENY))),
                new FakeClientFactory());

        var resolved = resolver.resolveTools(app());

        assertThat(resolved).extracting(t -> t.toolName()).containsExactly("read_file", "write_file");
        assertThat(resolved).extracting(t -> t.serverName()).containsOnly("fs");
        assertThat(resolved.get(0).connection()).isNotNull();
    }

    @Test
    void skipsDisabledAndUnavailableServers() {
        var resolver = new AgentMcpToolResolver(
                new RecordingBindingRepository(List.of(1L, 2L)),
                new RecordingServerRepository(List.of(
                        server(1L, "active"),
                        new McpServer(2L, "disabled", McpTransportType.SSE, "https://x", List.of(),
                                Map.of(), Map.of(), 30, McpServerStatus.DISABLED, null, null, null))),
                new RecordingToolRepository(List.of(
                        tool(1L, "tool_a", McpToolPermission.ALLOW),
                        tool(2L, "tool_b", McpToolPermission.ALLOW))),
                new FlakyClientFactory());

        var resolved = resolver.resolveTools(app());

        // server 2 disabled -> skipped; server 1 available -> resolved
        assertThat(resolved).extracting(t -> t.toolName()).containsExactly("tool_a");
    }

    @Test
    void skipsServerWhoseConnectionFails() {
        var resolver = new AgentMcpToolResolver(
                new RecordingBindingRepository(List.of(1L)),
                new RecordingServerRepository(server(1L, "broken")),
                new RecordingToolRepository(List.of(tool(1L, "tool_a", McpToolPermission.ALLOW))),
                new FlakyClientFactory());

        var resolved = resolver.resolveTools(app());

        assertThat(resolved).isEmpty();
    }

    private static Agent app() {
        return new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 1L, 1L);
    }

    private static McpServer server(Long id, String name) {
        return new McpServer(id, name, McpTransportType.STREAMABLE_HTTP, "https://mcp/" + id,
                List.of(), Map.of(), Map.of(), 30, McpServerStatus.ACTIVE, null, null, null);
    }

    private static McpToolDefinition tool(Long serverId, String name, McpToolPermission permission) {
        return new McpToolDefinition(serverId * 100, serverId, name, "desc",
                Map.of("type", "object"), false, permission, Instant.now());
    }

    private static final class RecordingBindingRepository implements AgentMcpBindingRepository {
        private final List<Long> serverIds;

        private RecordingBindingRepository(List<Long> serverIds) {
            this.serverIds = serverIds;
        }

        @Override
        public void save(String agentKey, List<Long> mcpServerIds) {
        }

        @Override
        public List<Long> findByAgentKey(String agentKey) {
            return serverIds;
        }

        @Override
        public void delete(String agentKey) {
        }
    }

    private static final class RecordingServerRepository implements McpServerRepository {
        private final List<McpServer> servers;

        private RecordingServerRepository(McpServer... servers) {
            this.servers = List.of(servers);
        }

        private RecordingServerRepository(List<McpServer> servers) {
            this.servers = servers;
        }

        @Override
        public McpServer save(McpServer server) {
            return server;
        }

        @Override
        public McpServer update(McpServer server) {
            return server;
        }

        @Override
        public McpServer findById(Long id) {
            return servers.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<McpServer> list() {
            return servers;
        }

        @Override
        public void disable(Long id) {
        }
    }

    private static final class RecordingToolRepository implements McpToolRepository {
        private final List<McpToolDefinition> tools;

        private RecordingToolRepository(List<McpToolDefinition> tools) {
            this.tools = tools;
        }

        private RecordingToolRepository() {
            this(List.of());
        }

        @Override
        public McpToolDefinition upsert(McpToolDefinition tool) {
            return tool;
        }

        @Override
        public List<McpToolDefinition> findByServerId(Long serverId) {
            return tools.stream().filter(t -> t.serverId().equals(serverId)).toList();
        }

        @Override
        public McpToolDefinition findByServerAndName(Long serverId, String toolName) {
            return null;
        }

        @Override
        public void updatePermission(Long serverId, String toolName, McpToolPermission permission) {
        }

        @Override
        public void deleteByServerId(Long serverId) {
        }

        @Override
        public void deleteByServerIdNotIn(Long serverId, List<String> keepToolNames) {
        }
    }

    private static final class FakeClientFactory implements McpClientFactory {
        @Override
        public McpConnection connect(McpServer server) {
            return new FakeConnection();
        }
    }

    private static final class FlakyClientFactory implements McpClientFactory {
        @Override
        public McpConnection connect(McpServer server) {
            if ("broken".equals(server.name())) {
                throw new IllegalStateException("connection refused");
            }
            return new FakeConnection();
        }
    }

    private static final class FakeConnection implements McpConnection {
        private boolean closed;

        @Override
        public List<McpToolInfo> listTools() {
            return List.of();
        }

        @Override
        public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
            return McpToolCallResult.success("ok");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
