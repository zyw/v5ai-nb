package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.mcp.McpClientFactory;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolInfo;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;
import xin.v5ai.nb.mcp.domain.McpServer;
import xin.v5ai.nb.mcp.domain.McpTool;
import xin.v5ai.nb.mcp.mapper.AgentMcpBindingMapper;
import xin.v5ai.nb.mcp.mapper.McpServerMapper;
import xin.v5ai.nb.mcp.mapper.McpToolMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMcpToolResolverTest {

    @Test
    void resolvesNothingWhenNoBindings() {
        var resolver = new AgentMcpToolResolver(
                bindingMapper(List.of()),
                serverMapper(),
                toolMapper(List.of()),
                new FakeClientFactory());
        assertThat(resolver.resolveTools(app())).isEmpty();
    }

    @Test
    void resolvesToolsAndFiltersDenied() {
        var resolver = new AgentMcpToolResolver(
                bindingMapper(List.of(binding(1L))),
                serverMapper(server(1L, "fs", "ACTIVE")),
                toolMapper(List.of(
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
                bindingMapper(List.of(binding(1L), binding(2L))),
                serverMapper(server(1L, "active", "ACTIVE"), server(2L, "disabled", "DISABLED")),
                toolMapper(List.of(tool(1L, "tool_a", McpToolPermission.ALLOW))),
                new FlakyClientFactory());

        var resolved = resolver.resolveTools(app());

        // server 2 disabled -> skipped（状态检查先于工具读取）；server 1 available -> resolved
        assertThat(resolved).extracting(t -> t.toolName()).containsExactly("tool_a");
    }

    @Test
    void skipsServerWhoseConnectionFails() {
        var resolver = new AgentMcpToolResolver(
                bindingMapper(List.of(binding(1L))),
                serverMapper(server(1L, "broken", "ACTIVE")),
                toolMapper(List.of(tool(1L, "tool_a", McpToolPermission.ALLOW))),
                new FlakyClientFactory());

        var resolved = resolver.resolveTools(app());

        assertThat(resolved).isEmpty();
    }

    private static Agent app() {
        return new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 1L, 1L);
    }

    private static AgentMcpBindingMapper bindingMapper(List<AgentMcpBinding> bindings) {
        AgentMcpBindingMapper mapper = mock(AgentMcpBindingMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(bindings);
        return mapper;
    }

    private static McpServerMapper serverMapper(McpServer... servers) {
        McpServerMapper mapper = mock(McpServerMapper.class);
        when(mapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return List.of(servers).stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
        });
        return mapper;
    }

    private static McpToolMapper toolMapper(List<McpTool> tools) {
        McpToolMapper mapper = mock(McpToolMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(tools);
        return mapper;
    }

    private static AgentMcpBinding binding(Long serverId) {
        var binding = new AgentMcpBinding();
        binding.setAgentKey("demo");
        binding.setMcpServerId(serverId);
        return binding;
    }

    private static McpServer server(Long id, String name, String status) {
        var server = new McpServer();
        server.setId(id);
        server.setName(name);
        server.setTransportType("SSE");
        server.setStatus(status);
        return server;
    }

    private static McpTool tool(Long serverId, String name, McpToolPermission permission) {
        var tool = new McpTool();
        tool.setId(serverId * 100);
        tool.setServerId(serverId);
        tool.setToolName(name);
        tool.setDescription("desc");
        tool.setInputSchemaJson("{\"type\":\"object\"}");
        tool.setPermission(permission.name());
        return tool;
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
            if ("broken".equals(server.getName())) {
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
