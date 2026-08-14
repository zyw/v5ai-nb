package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.AgentTextEvent;

import java.util.List;
import java.util.Map;

class PublishedModelAgentTextExecutorTest {
    @Test
    void resolvesAgentScopeModelFromPublishedApplicationModelId() {
        var executor = new PublishedModelAgentTextExecutor(
                modelId -> new FixedTextModel("model-" + modelId),
                model -> (application, request) -> Flux.just(new AgentTextEvent.Text(model.getModelName())));

        StepVerifier.create(executor.streamText(
                        new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 7L, 1L),
                        new AgentRunRequest("demo", "conversation-1", "hello")))
                .expectNext(new AgentTextEvent.Text("fixed:model-7"))
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
            return "fixed:" + response;
        }
    }
}
