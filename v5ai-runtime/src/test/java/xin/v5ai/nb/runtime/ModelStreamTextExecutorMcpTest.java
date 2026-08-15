package xin.v5ai.nb.runtime;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpToolCallAudit;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolInfo;
import xin.v5ai.nb.mcp.McpToolPermission;
import xin.v5ai.nb.skill.ResolvedSkill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归测试：MCP 工具注册路径（HarnessAgent + streamEvents）不能因无关事件
 * 映射为 null 而触发 Reactor "mapper returned a null value" 导致 RUN_FAILED。
 *
 * 说明：HarnessAgent 会在工作目录写入 agents/sessions 文件，Windows 上
 * JUnit @TempDir 的递归删除会与文件句柄竞争（DirectoryNotEmptyException），
 * 因此这里自建临时目录并做 best-effort 清理。
 */
class ModelStreamTextExecutorMcpTest {
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        if (tempDir == null) {
            return;
        }
        try {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort：Windows 文件句柄竞争时忽略
                    }
                });
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    @Test
    void streamWithToolsCompletesWithoutNullMapperError() {
        tempDir = createTempDir();
        var executor = new ModelStreamTextExecutor(
                new FixedTextModel("agent-scope-ok"),
                application -> List.of(new ResolvedMcpTool(
                        1L, "demo-mcp", "fetch_url", "fetch a url",
                        Map.of("type", "object"), McpToolPermission.ALLOW, new RecordingConnection())),
                new RecordingAuditRepository(),
                tempDir.resolve("workspace"));

        StepVerifier.create(executor.streamText(
                        new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L),
                        new AgentRunRequest("demo", "c1", "hello", null, List.of(), "run-1")))
                .expectNextMatches(event -> event instanceof AgentTextEvent.ModelCall call
                        && call.modelName().equals("fixed-text"))
                .expectNextMatches(event -> event instanceof AgentTextEvent.Text text
                        && text.text().contains("agent-scope-ok"))
                .verifyComplete();
    }

    @Test
    void consecutiveRunsAreIsolatedFromPendingToolState() {
        // 回归：上一次运行中断残留的 pending tool call 不得泄漏到下一次运行
        tempDir = createTempDir();
        var executor = new ModelStreamTextExecutor(
                new FixedTextModel("agent-scope-ok"),
                application -> List.of(new ResolvedMcpTool(
                        1L, "demo-mcp", "fetch_url", "fetch a url",
                        Map.of("type", "object"), McpToolPermission.ALLOW, new RecordingConnection())),
                new RecordingAuditRepository(),
                tempDir.resolve("workspace"));
        var application = new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L);

        for (int i = 0; i < 2; i++) {
            StepVerifier.create(executor.streamText(application,
                            new AgentRunRequest("demo", "c1", "hello", null, List.of(), "run-" + i)))
                    .expectNextMatches(event -> event instanceof AgentTextEvent.ModelCall)
                    .expectNextMatches(event -> event instanceof AgentTextEvent.Text)
                    .verifyComplete();
        }
    }

    @Test
    void streamDirectWithoutToolsStillWorks() {
        var executor = new ModelStreamTextExecutor(new FixedTextModel("direct-ok"));
        StepVerifier.create(executor.streamText(
                        new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L),
                        new AgentRunRequest("demo", "c1", "hello")))
                .expectNextMatches(event -> event instanceof AgentTextEvent.ModelCall)
                .expectNextMatches(event -> event instanceof AgentTextEvent.Text text
                        && text.text().contains("direct-ok"))
                .verifyComplete();
    }

    @Test
    void streamWithSkillsInjectsWorkspaceFilesAndCompletes() throws IOException {
        tempDir = createTempDir();
        Path workspace = tempDir.resolve("workspace");
        var executor = new ModelStreamTextExecutor(
                new FixedTextModel("skill-ok"),
                null,
                null,
                application -> List.of(new ResolvedSkill("weather", "Get weather", Map.of(
                        "SKILL.md", "---\nname: weather\ndescription: Get weather\n---\nUse the tool.",
                        "resources/data.txt", "data"), 5L)),
                workspace);

        StepVerifier.create(executor.streamText(
                        new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L),
                        new AgentRunRequest("demo", "c1", "hello", null, List.of(), "run-1")))
                .expectNextMatches(event -> event instanceof AgentTextEvent.ModelCall)
                .expectNextMatches(event -> event instanceof AgentTextEvent.Text)
                .verifyComplete();

        // Workspace 注入断言：已发布 Skill 文件写入 <workspace>/skills/<name>/
        assertThat(Files.exists(workspace.resolve("skills/weather/SKILL.md"))).isTrue();
        assertThat(Files.readString(workspace.resolve("skills/weather/resources/data.txt"))).isEqualTo("data");
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("v5ai-mcp-executor-test");
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to create temp dir", exception);
        }
    }

    private static final class RecordingConnection implements McpConnection {
        private int closed;

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
            closed++;
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

    private static final class FixedTextModel implements Model {
        private final String response;

        private FixedTextModel(String response) {
            this.response = response;
        }

        @Override
        public Flux<ChatResponse> stream(List<io.agentscope.core.message.Msg> messages,
                                         List<ToolSchema> tools,
                                         GenerateOptions options) {
            return Flux.just(ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text(response).build()))
                    .metadata(Map.of())
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "fixed-text";
        }
    }
}
