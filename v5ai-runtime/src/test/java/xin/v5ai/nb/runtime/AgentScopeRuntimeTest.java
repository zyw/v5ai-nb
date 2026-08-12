package xin.v5ai.nb.runtime;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationPublishException;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.PublishedApplicationResolver;

class AgentScopeRuntimeTest {
    @Test
    void streamConvertsAgentTextToPlatformTextDelta() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Application("demo", "Demo", ApplicationStatus.PUBLISHED, 7L, 1L)),
                (application, request) -> Flux.just("hello", " world")
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", "conversation-1", "hi")))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_STARTED)
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals("hello"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.TEXT_DELTA && event.payload().equals(" world"))
                .expectNextMatches(event -> event.type() == RuntimeEventType.RUN_COMPLETED)
                .verifyComplete();
    }

    @Test
    void runtimeRejectsUnpublishedApplication() {
        var runtime = new AgentScopeRuntime(
                resolverWith(new Application("demo", "Demo", ApplicationStatus.DRAFT, 7L, null)),
                (application, request) -> Flux.just("never")
        );

        StepVerifier.create(runtime.stream(new AgentRunRequest("demo", null, "hi")))
                .expectError(ApplicationPublishException.class)
                .verify();
    }

    private PublishedApplicationResolver resolverWith(Application application) {
        return new PublishedApplicationResolver(new InMemoryApplicationRepository(application));
    }

    private static final class InMemoryApplicationRepository implements ApplicationRepository {
        private final Application application;

        private InMemoryApplicationRepository(Application application) {
            this.application = application;
        }

        @Override
        public Application findByAppKey(String appKey) {
            return appKey.equals(application.appKey()) ? application : null;
        }

        @Override
        public void save(Application application) {
        }

        @Override
        public void publish(String appKey, long version) {
        }
    }
}
