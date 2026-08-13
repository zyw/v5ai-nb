package xin.v5ai.nb.api.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.api.apiKey.ApplicationApiKeyAuthenticationFilter;
import xin.v5ai.nb.runtime.AgentRunRequest;
import xin.v5ai.nb.runtime.AgentRunResult;
import xin.v5ai.nb.runtime.AgentRuntime;
import xin.v5ai.nb.runtime.RuntimeEvent;

class ChatControllerTest {
    @Test
    void streamEndpointReturnsTypedSseEvents() {
        var client = chatClient(new ApplicationApiKeyAuthenticationFilter((appKey, apiKey) -> true));

        client.post()
                .uri("/api/v1/apps/demo/chat/stream")
                .header("Authorization", "Bearer app-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("conversation-1", "hello"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("RUN_STARTED")
                        .contains("TEXT_DELTA")
                        .contains("RUN_COMPLETED"));
    }

    @Test
    void streamEndpointRejectsMissingApiKey() {
        var client = chatClient(new ApplicationApiKeyAuthenticationFilter((appKey, apiKey) -> true));

        client.post()
                .uri("/api/v1/apps/demo/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("conversation-1", "hello"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void streamEndpointRejectsInvalidApiKeyForApp() {
        var client = chatClient(new ApplicationApiKeyAuthenticationFilter((appKey, apiKey) -> false));

        client.post()
                .uri("/api/v1/apps/demo/chat/stream")
                .header("Authorization", "Bearer wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("conversation-1", "hello"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private WebTestClient chatClient(WebFilter filter) {
        return WebTestClient.bindToController(new ChatController(new StubAgentRuntime()))
                .webFilter(filter)
                .configureClient()
                .build();
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
