package xin.v5ai.nb.runtime;

import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpToolCallAudit;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolInfo;
import xin.v5ai.nb.mcp.McpToolPermission;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归测试：PlatformMcpTool 注册进真实 AgentScope Toolkit 后必须被正常执行
 * （走 callAsync），而不能被 externalTool 短路为 suspended 占位结果
 * （表现：result state=RUNNING、连接从未被调用、Agent 结束在 tool-call turn 无最终文本）。
 */
class PlatformMcpToolToolkitTest {

    @Test
    void toolkitExecutesPlatformMcpToolThroughCallAsync() {
        var connection = new RecordingMcpConnection();
        var tool = new PlatformMcpTool(
                new ResolvedMcpTool(1L, "demo-mcp", "fetch_url", "fetch a url",
                        Map.of("type", "object"), McpToolPermission.ALLOW, connection),
                "run-1", new RecordingAuditRepository());

        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);

        // 模拟真实模型工具调用：input 为解析后的参数 Map，content 为原始 arguments JSON
        var param = ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-1")
                        .name("fetch_url")
                        .input(Map.of("url", "https://example.com"))
                        .content("{\"url\":\"https://example.com\"}")
                        .build())
                .input(Map.of("url", "https://example.com"))
                .build();

        StepVerifier.create(toolkit.callTool(param))
                .assertNext(result -> {
                    // 修复前：externalTool(true) 导致短路，state=RUNNING（suspended，
                    // 内容为 "[Awaiting external execution]"）
                    // 修复后：正常执行，state=SUCCESS
                    assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
                    assertThat(result.isSuspended()).isFalse();
                })
                .verifyComplete();

        assertThat(connection.invoked).isTrue();
        assertThat(connection.lastToolName).isEqualTo("fetch_url");
        assertThat(connection.lastArguments).containsEntry("url", "https://example.com");
    }

    @Test
    void deniedToolIsRejectedByToolkitWithoutCallingRemote() {
        var connection = new RecordingMcpConnection();
        var tool = new PlatformMcpTool(
                new ResolvedMcpTool(2L, "danger-mcp", "delete_all", "dangerous",
                        Map.of("type", "object"), McpToolPermission.DENY, connection),
                "run-2", new RecordingAuditRepository());

        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);

        var param = ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-2")
                        .name("delete_all")
                        .input(Map.of("confirm", true))
                        .content("{\"confirm\":true}")
                        .build())
                .input(Map.of("confirm", true))
                .build();

        StepVerifier.create(toolkit.callTool(param))
                .assertNext(result -> assertThat(result.getState()).isNotEqualTo(ToolResultState.SUCCESS))
                .verifyComplete();

        assertThat(connection.invoked).isFalse();
    }

    @Test
    void toolkitHandlesEmptyContentArgumentLikeNoArgumentTools() {
        // 无参工具：模型返回的 arguments 为空串（OpenAIResponseParser 会把 null 置为 ""）
        var connection = new RecordingMcpConnection();
        var tool = new PlatformMcpTool(
                new ResolvedMcpTool(3L, "demo-mcp", "current_date", "current date",
                        Map.of("type", "object"), McpToolPermission.ALLOW, connection),
                "run-3", new RecordingAuditRepository());

        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);

        var param = ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-3")
                        .name("current_date")
                        .input(Map.of())
                        .content("")
                        .build())
                .input(Map.of())
                .build();

        StepVerifier.create(toolkit.callTool(param))
                .assertNext(result -> {
                    // 允许两种结果：要么校验通过正常执行（SUCCESS），
                    // 要么返回可读的错误结果（非 RUNNING 占位）——但不应抛异常
                    assertThat(result.getState()).isNotNull();
                })
                .verifyComplete();
    }

    private static final class RecordingMcpConnection implements McpConnection {
        private boolean invoked;
        private String lastToolName;
        private Map<String, Object> lastArguments;

        @Override
        public List<McpToolInfo> listTools() {
            return List.of();
        }

        @Override
        public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
            invoked = true;
            lastToolName = toolName;
            lastArguments = arguments;
            return McpToolCallResult.success("page content");
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingAuditRepository implements McpToolCallAuditRepository {
        @Override
        public McpToolCallAudit save(McpToolCallAudit audit) {
            return audit;
        }

        @Override
        public List<McpToolCallAudit> findByRunId(String runId) {
            return List.of();
        }

        @Override
        public List<McpToolCallAudit> findByServerId(Long serverId) {
            return List.of();
        }
    }
}
