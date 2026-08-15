package xin.v5ai.nb.runtime;

import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpToolCallAudit;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolCallStatus;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.mcp.McpToolInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMcpToolTest {
    @Test
    void allowToolExecutesAndRecordsSuccessfulAudit() {
        var connection = new RecordingMcpConnection(Map.of("city", "Berlin"), "20 C");
        var audits = new RecordingAuditRepository();
        var tool = new PlatformMcpTool(
                new ResolvedMcpTool(1L, "weather-mcp", "get_weather", "weather lookup",
                        Map.of("type", "object"), McpToolPermission.ALLOW, connection),
                "run-123", audits);

        StepVerifier.create(tool.callAsync(param(Map.of("city", "Berlin"))))
                .assertNext(result -> {
                    assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
                })
                .verifyComplete();

        assertThat(connection.calledWith).containsExactly(Map.of("city", "Berlin"));
        assertThat(audits.stored).hasSize(1);
        var audit = audits.stored.get(0);
        assertThat(audit.runId()).isEqualTo("run-123");
        assertThat(audit.serverId()).isEqualTo(1L);
        assertThat(audit.serverName()).isEqualTo("weather-mcp");
        assertThat(audit.toolName()).isEqualTo("get_weather");
        assertThat(audit.decision()).isEqualTo(McpToolPermission.ALLOW);
        assertThat(audit.status()).isEqualTo(McpToolCallStatus.SUCCESS);
        assertThat(audit.argumentsSummary()).contains("Berlin");
        assertThat(audit.durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void failedToolCallRecordsFailedAuditAndReturnsErrorBlock() {
        var connection = new RecordingMcpConnection(Map.of(), "remote error");
        connection.fail = true;
        var audits = new RecordingAuditRepository();
        var tool = new PlatformMcpTool(
                new ResolvedMcpTool(2L, "git-mcp", "git_status", "git status",
                        Map.of("type", "object"), McpToolPermission.ALLOW, connection),
                "run-456", audits);

        StepVerifier.create(tool.callAsync(param(Map.of())))
                .assertNext(result -> assertThat(result.getState()).isNotEqualTo(ToolResultState.SUCCESS))
                .verifyComplete();

        assertThat(audits.stored).hasSize(1);
        assertThat(audits.stored.get(0).status()).isEqualTo(McpToolCallStatus.FAILED);
        assertThat(audits.stored.get(0).message()).contains("remote error");
    }

    @Test
    void deniedToolIsRejectedWithoutCallingRemote() {
        var connection = new RecordingMcpConnection(Map.of(), "should not be called");
        var audits = new RecordingAuditRepository();
        var tool = new PlatformMcpTool(
                new ResolvedMcpTool(3L, "danger-mcp", "delete_all", "dangerous",
                        Map.of("type", "object"), McpToolPermission.DENY, connection),
                "run-789", audits);

        StepVerifier.create(tool.callAsync(param(Map.of("confirm", true))))
                .assertNext(result -> assertThat(result.getState()).isNotEqualTo(ToolResultState.SUCCESS))
                .verifyComplete();

        assertThat(connection.invoked).isFalse();
        assertThat(audits.stored).hasSize(1);
        assertThat(audits.stored.get(0).status()).isEqualTo(McpToolCallStatus.DENIED);
        assertThat(audits.stored.get(0).decision()).isEqualTo(McpToolPermission.DENY);
    }

    private static ToolCallParam param(Map<String, Object> input) {
        return ToolCallParam.builder().input(input).build();
    }

    private static final class RecordingMcpConnection implements McpConnection {
        private final Map<String, Object> expectedArgs;
        private final String content;
        private final List<Map<String, Object>> calledWith = new ArrayList<>();
        private boolean fail;
        private boolean invoked;

        private RecordingMcpConnection(Map<String, Object> expectedArgs, String content) {
            this.expectedArgs = expectedArgs;
            this.content = content;
        }

        @Override
        public List<McpToolInfo> listTools() {
            return List.of();
        }

        @Override
        public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
            invoked = true;
            calledWith.add(arguments);
            return fail ? McpToolCallResult.failure(content) : McpToolCallResult.success(content);
        }

        @Override
        public void close() {
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
}
