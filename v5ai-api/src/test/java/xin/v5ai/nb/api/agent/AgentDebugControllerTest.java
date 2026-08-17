package xin.v5ai.nb.api.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.api.runtime.ChatRequest;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.AgentRunResult;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.RuntimeEvent;

class AgentDebugControllerTest {

    @Test
    void adminStreamEndpointReturnsTypedSseEventsWithoutApiKey() {
        var client = WebTestClient.bindToController(new AgentDebugController(new StubAgentRuntime()))
                .configureClient()
                .build();

        client.post()
                .uri("/api/admin/agents/demo/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest(null, "hello"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("RUN_STARTED")
                        .contains("TEXT_DELTA")
                        .contains("RUN_COMPLETED"));
    }

    private static final class StubAgentRuntime implements AgentRuntime {
        @Override
        public Flux<RuntimeEvent> stream(AgentRunRequest request) {
            return Flux.just(
                    RuntimeEvent.started("run-1"),
                    RuntimeEvent.textDelta("run-1", "hi"),
                    RuntimeEvent.completed("run-1")
            );
        }

        @Override
        public Mono<AgentRunResult> call(AgentRunRequest request) {
            return Mono.just(new AgentRunResult("run-1", "hi"));
        }
    }
}
