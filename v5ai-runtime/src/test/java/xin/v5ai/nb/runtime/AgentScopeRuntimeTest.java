package xin.v5ai.nb.runtime;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentPublishException;
import xin.v5ai.nb.agent.AgentRepository;
import xin.v5ai.nb.agent.AgentStatus;
import xin.v5ai.nb.agent.AgentVersion;
import xin.v5ai.nb.agent.PublishedAgentResolver;
import xin.v5ai.nb.mcp.McpToolPermission;

import java.util.List;

class AgentScopeRuntimeTest {
    @Test
    void streamConvertsAgentTextToPlatformTextDelta() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L)),
                (application, request) -> Flux.just(new AgentTextEvent.Text("hello"), new AgentTextEvent.Text(" world"))
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hi")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals("hello"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals(" world"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    @Test
    void streamEmitsModelCallAndRetrievalEvents() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L)),
                (application, request) -> Flux.just(new AgentTextEvent.ModelCall("gpt-4o"), new AgentTextEvent.Text("answer")),
                (application, request) -> reactor.core.publisher.Mono.just("[knowledge context]")
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hi")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.RETRIEVAL && event.payload().contains("knowledge"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.MODEL_CALL && event.payload().equals("gpt-4o"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals("answer"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    @Test
    void runtimeRejectsUnpublishedApplication() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Agent("demo", "Demo", null, AgentStatus.DRAFT, 7L, null)),
                (application, request) -> Flux.just(new AgentTextEvent.Text("never"))
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", null, "hi")))
                .expectError(AgentPublishException.class)
                .verify();
    }

    @Test
    void debugResolverAllowsDraftApplication() {
        var draft = new Agent("draft", "Draft", null, AgentStatus.DRAFT, 7L, null);
        // 调试运行时：直接注入 Function 解析器（等价于 PublishedAgentResolver::resolveForDebug）
        var runtime = new AgentScopeRuntime(
                agentKey -> agentKey.equals("draft") ? draft : null,
                (application, request) -> Flux.just(new AgentTextEvent.Text("draft answer")),
                null
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("draft", "conversation-1", "hi")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals("draft answer"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    @Test
    void streamEmitsToolCallEvents() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L)),
                (application, request) -> Flux.just(
                        new AgentTextEvent.ToolCall("weather", McpToolPermission.ALLOW),
                        new AgentTextEvent.ToolResult("weather", true, ""),
                        new AgentTextEvent.Text("23 degrees"))
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "weather?")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TOOL_CALL
                        && event.payload().contains("weather") && event.payload().contains("ALLOW"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.TOOL_RESULT
                        && event.payload().contains("weather"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals("23 degrees"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    @Test
    void streamEmitsPermissionRequiredForApproveTool() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L)),
                (application, request) -> Flux.just(
                        new AgentTextEvent.ToolCall("delete-file", McpToolPermission.APPROVE),
                        new AgentTextEvent.ToolResult("delete-file", true, ""))
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "delete it")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TOOL_CALL)
                .expectNextMatches(event -> event.type() == RuntimeEventType.PERMISSION_REQUIRED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TOOL_RESULT)
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    @Test
    void streamEmitsFailedToolResult() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Agent("demo", "Demo", null, AgentStatus.PUBLISHED, 7L, 1L)),
                (application, request) -> Flux.just(
                        new AgentTextEvent.ToolCall("git", McpToolPermission.ALLOW),
                        new AgentTextEvent.ToolResult("git", false, "repo not found"))
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "git status")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TOOL_CALL)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TOOL_RESULT
                        && event.payload().contains("repo not found"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    private PublishedAgentResolver resolverWith(Agent application) {
        return new PublishedAgentResolver(new InMemoryAgentRepository(application));
    }

    private static final class InMemoryAgentRepository implements AgentRepository {
        private final Agent application;

        private InMemoryAgentRepository(Agent application) {
            this.application = application;
        }

        @Override
        public Agent findByAgentKey(String agentKey) {
            return agentKey.equals(application.agentKey()) ? application : null;
        }

        @Override
        public void save(Agent application) {
        }

        @Override
        public void update(Agent application) {
        }

        @Override
        public void disable(String agentKey) {
        }

        @Override
        public List<Agent> list() {
            return List.of(application);
        }

        @Override
        public long nextVersion(String agentKey) {
            return 1;
        }

        @Override
        public void saveVersion(AgentVersion version) {
        }

        @Override
        public List<AgentVersion> listVersions(String agentKey) {
            return List.of();
        }

        @Override
        public void publish(String agentKey, long version) {
        }
    }
}
