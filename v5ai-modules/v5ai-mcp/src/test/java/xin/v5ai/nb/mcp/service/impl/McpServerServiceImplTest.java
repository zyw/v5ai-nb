package xin.v5ai.nb.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.McpConnection;
import xin.v5ai.nb.common.agentscope.core.StdioCommandPolicy;
import xin.v5ai.nb.common.agentscope.core.domain.vo.McpToolCallVo;
import xin.v5ai.nb.common.agentscope.core.domain.McpToolInfo;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.common.agentscope.enums.McpTransportType;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.mcp.core.McpClientFactory;
import xin.v5ai.nb.mcp.core.domain.dto.McpServerDTO;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;
import xin.v5ai.nb.mcp.domain.McpServer;
import xin.v5ai.nb.mcp.domain.McpTool;
import xin.v5ai.nb.mcp.domain.McpToolCallAudit;
import xin.v5ai.nb.mcp.domain.bo.McpServerBo;
import xin.v5ai.nb.mcp.mapper.AgentMcpBindingMapper;
import xin.v5ai.nb.mcp.mapper.McpServerMapper;
import xin.v5ai.nb.mcp.mapper.McpToolCallAuditMapper;
import xin.v5ai.nb.mcp.mapper.McpToolMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class McpServerServiceImplTest {

    private final List<McpServer> servers = new ArrayList<>();
    private final List<McpTool> tools = new ArrayList<>();
    private final List<McpToolCallAudit> audits = new ArrayList<>();
    private final List<AgentMcpBinding> bindings = new ArrayList<>();

    private McpServerServiceImpl service;

    @BeforeEach
    void setUp() {
        McpServerMapper serverMapper = mock(McpServerMapper.class);
        McpToolMapper toolMapper = mock(McpToolMapper.class);
        McpToolCallAuditMapper auditMapper = mock(McpToolCallAuditMapper.class);
        AgentMcpBindingMapper bindingMapper = mock(AgentMcpBindingMapper.class);
        var idGen = new AtomicLong(1);

        when(serverMapper.selectById(anyLong())).thenAnswer(inv -> findServer(inv.getArgument(0)));
        when(serverMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(servers));
        doAnswer(inv -> {
            var server = inv.getArgument(0, McpServer.class);
            if (server.getId() == null) {
                server.setId(idGen.getAndIncrement());
            }
            servers.add(server);
            return 1;
        }).when(serverMapper).insert(any(McpServer.class));
        doAnswer(inv -> {
            McpServer update = inv.getArgument(0, McpServer.class);
            servers.stream().filter(s -> s.getId().equals(update.getId())).forEach(s -> {
                if (update.getStatus() != null) {
                    s.setStatus(update.getStatus());
                }
                if (update.getLastTestStatus() != null) {
                    s.setLastTestStatus(update.getLastTestStatus());
                }
                if (update.getLastTestMessage() != null) {
                    s.setLastTestMessage(update.getLastTestMessage());
                }
                if (update.getLastTestedAt() != null) {
                    s.setLastTestedAt(update.getLastTestedAt());
                }
            });
            return 1;
        }).when(serverMapper).updateById(any(McpServer.class));

        when(toolMapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> tools.isEmpty() ? null : tools.get(0));
        when(toolMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(tools));
        doAnswer(inv -> {
            var tool = inv.getArgument(0, McpTool.class);
            tool.setId(idGen.getAndIncrement());
            tools.add(tool);
            return 1;
        }).when(toolMapper).insert(any(McpTool.class));
        doAnswer(inv -> {
            McpTool update = inv.getArgument(0, McpTool.class);
            tools.stream().filter(t -> t.getId().equals(update.getId())).forEach(t -> {
                if (update.getPermission() != null) {
                    t.setPermission(update.getPermission());
                }
            });
            return 1;
        }).when(toolMapper).updateById(any(McpTool.class));

        when(auditMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(audits));

        when(bindingMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(bindings));
        doAnswer(inv -> {
            var binding = inv.getArgument(0, AgentMcpBinding.class);
            bindings.add(binding);
            return 1;
        }).when(bindingMapper).insert(any(AgentMcpBinding.class));
        doAnswer(inv -> {
            bindings.removeIf(b -> b.getAgentKey().equals(inv.getArgument(0, String.class)));
            return 1;
        }).when(bindingMapper).delete(any(Wrapper.class));

        service = new McpServerServiceImpl(
                serverMapper, toolMapper, auditMapper, bindingMapper,
                new FakeClientFactory(), new NoOpStdioPolicy(), new FakeCipher());
    }

    @Test
    void createServerValidatesAndEncryptsSecrets() {
        var bo = new McpServerBo();
        bo.setName("weather");
        bo.setTransportType(McpTransportType.STREAMABLE_HTTP);
        bo.setEndpoint("https://mcp.example.com");
        bo.setHeaders(Map.of("Authorization", "Bearer x"));
        bo.setEnvVars(Map.of("K", "V"));

        var vo = service.createServer(bo);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(vo.getTimeoutSeconds()).isEqualTo(30);
        // VO 中为解密后的明文
        assertThat(vo.getHeaders()).containsEntry("Authorization", "Bearer x");
        // 落库为密文
        var stored = servers.get(0);
        assertThat(stored.getHeadersCiphertext()).startsWith("enc:");
        assertThat(stored.getEnvCiphertext()).startsWith("enc:");
    }

    @Test
    void createServerRejectsBlankNameOrEndpoint() {
        var bo = new McpServerBo();
        bo.setTransportType(McpTransportType.SSE);
        assertThatThrownBy(() -> service.createServer(bo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");

        var bo2 = new McpServerBo();
        bo2.setName("x");
        bo2.setTransportType(McpTransportType.SSE);
        assertThatThrownBy(() -> service.createServer(bo2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint is required");
    }

    @Test
    void testConnectionRecordsLastTestResult() {
        var created = service.createServer(serverBo("ok", McpTransportType.SSE, "https://ok"));

        var result = service.testConnection(created.getId());

        assertThat(result.ok()).isTrue();
        assertThat(result.toolCount()).isEqualTo(1);
        var stored = servers.stream().filter(s -> s.getId().equals(created.getId())).findFirst().orElseThrow();
        assertThat(stored.getLastTestStatus()).isEqualTo("ok");
    }

    @Test
    void discoverToolsUpsertsSingleTool() {
        var created = service.createServer(serverBo("fs", McpTransportType.SSE, "https://fs"));
        var discovered = service.discoverTools(created.getId());

        assertThat(discovered).extracting(vo -> vo.getToolName()).containsExactly("read_file");
        assertThat(discovered.get(0).getInputSchema()).containsEntry("type", "object");
        // 可写 Tool 默认 APPROVE（需人工审批）
        assertThat(tools).extracting(McpTool::getPermission).containsExactly("APPROVE");
    }

    @Test
    void updateToolPermissionUpdatesStoredTool() {
        var created = service.createServer(serverBo("fs", McpTransportType.SSE, "https://fs"));
        service.discoverTools(created.getId());

        var updated = service.updateToolPermission(created.getId(), "read_file", McpToolPermission.DENY);

        assertThat(updated.getPermission()).isEqualTo("DENY");
        assertThat(service.listTools(created.getId()).get(0).getPermission()).isEqualTo("DENY");
    }

    @Test
    void updateToolPermissionRejectsUnknownTool() {
        var created = service.createServer(serverBo("fs", McpTransportType.SSE, "https://fs"));
        // 未发现任何工具 → 工具不存在
        assertThatThrownBy(() -> service.updateToolPermission(created.getId(), "missing", McpToolPermission.ALLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void bindRequiresActiveServer() {
        var created = service.createServer(serverBo("s", McpTransportType.SSE, "https://s"));
        service.disableServer(created.getId());

        assertThatThrownBy(() -> service.bindServers("demo", List.of(created.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");

        var active = service.createServer(serverBo("a", McpTransportType.SSE, "https://a"));
        service.bindServers("demo", List.of(active.getId()));
        assertThat(service.getServerBindings("demo")).containsExactly(active.getId());
    }

    @Test
    void enableServerRestoresActiveAfterDisable() {
        var created = service.createServer(serverBo("s", McpTransportType.SSE, "https://s"));
        service.disableServer(created.getId());
        // 禁用期间不可绑定
        assertThatThrownBy(() -> service.bindServers("demo", List.of(created.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");

        service.enableServer(created.getId());
        service.bindServers("demo", List.of(created.getId()));
        assertThat(service.getServerBindings("demo")).containsExactly(created.getId());
    }

    @Test
    void listToolCallsByRunAndServer() {
        audits.add(audit("run-1", 1L));

        assertThat(service.listToolCallsByRun("run-1")).hasSize(1);
        assertThat(service.listToolCallsByServer(1L)).hasSize(1);
        assertThat(service.listToolCallsByRun(" ")).isEmpty();
    }

    private McpServer findServer(Object id) {
        return servers.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }

    private static McpServerBo serverBo(String name, McpTransportType type, String endpoint) {
        var bo = new McpServerBo();
        bo.setName(name);
        bo.setTransportType(type);
        bo.setEndpoint(endpoint);
        return bo;
    }

    private static McpToolCallAudit audit(String runId, Long serverId) {
        var audit = new McpToolCallAudit();
        audit.setRunId(runId);
        audit.setServerId(serverId);
        audit.setDecision("ALLOW");
        audit.setStatus("SUCCESS");
        return audit;
    }

    private static final class FakeClientFactory implements McpClientFactory {
        @Override
        public McpConnection connect(McpServerDTO server) {
            return new FakeConnection();
        }
    }

    private static final class FakeConnection implements McpConnection {
        @Override
        public List<McpToolInfo> listTools() {
            return List.of(new McpToolInfo("read_file", "read a file", Map.of("type", "object"), false));
        }

        @Override
        public McpToolCallVo callTool(String toolName, Map<String, Object> arguments) {
            return McpToolCallVo.success("ok");
        }

        @Override
        public void close() {
        }
    }

    private static final class NoOpStdioPolicy implements StdioCommandPolicy {
        @Override
        public void validate(String command) {
        }
    }

    private static final class FakeCipher implements CredentialCipher {
        @Override
        public String encrypt(String plaintext) {
            return "enc:" + plaintext;
        }

        @Override
        public String decrypt(String ciphertext) {
            return ciphertext == null || ciphertext.isBlank() ? null : ciphertext.substring(4);
        }
    }
}
