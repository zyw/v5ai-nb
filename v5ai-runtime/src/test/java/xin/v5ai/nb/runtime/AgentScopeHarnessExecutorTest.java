package xin.v5ai.nb.runtime;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class AgentScopeHarnessExecutorTest {
    @TempDir
    Path tempDir;

    @Test
    void delegatesTextExecutionThroughAgentScopeHarness() {
        var executor = new AgentScopeHarnessExecutor(
                new FixedTextModel("agent-scope-ok"),
                new JsonFileAgentStateStore(tempDir.resolve("state")),
                tempDir.resolve("workspace"));

        StepVerifier.create(executor.streamText(
                        new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L),
                        new AgentRunRequest("demo", "c1", "hello")))
                .expectNextMatches(event -> event instanceof AgentTextEvent.ModelCall call
                        && call.modelName().equals("fixed-text"))
                .expectNext(new AgentTextEvent.Text("agent-scope-ok"))
                .verifyComplete();
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
