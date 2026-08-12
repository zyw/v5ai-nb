package xin.v5ai.nb.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationManagementServiceTest {
    @Test
    void publishingApplicationWithoutModelIsRejected() {
        var service = new ApplicationManagementService(new InMemoryApplicationRepository(), modelId -> false);

        assertThatThrownBy(() -> service.publish("demo"))
                .isInstanceOf(ApplicationPublishException.class);
    }

    @Test
    void publishingApplicationUsesModelIdAndPublishesVersion() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", ApplicationStatus.DRAFT, 7L, 3L));
        var service = new ApplicationManagementService(repository, modelId -> modelId == 7L);

        service.publish("demo");

        var application = repository.findByAppKey("demo");
        org.assertj.core.api.Assertions.assertThat(application.status()).isEqualTo(ApplicationStatus.PUBLISHED);
        org.assertj.core.api.Assertions.assertThat(application.publishedVersion()).isEqualTo(3L);
    }

    @Test
    void publishedApplicationCanBeResolved() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", ApplicationStatus.PUBLISHED, 7L, 1L));
        var resolver = new PublishedApplicationResolver(repository);

        var application = resolver.resolve("demo");

        org.assertj.core.api.Assertions.assertThat(application.status()).isEqualTo(ApplicationStatus.PUBLISHED);
    }

    private static final class InMemoryApplicationRepository implements ApplicationRepository {
        private final java.util.Map<String, Application> applications = new java.util.HashMap<>();

        @Override public Application findByAppKey(String appKey) { return applications.get(appKey); }
        @Override public void save(Application application) { applications.put(application.appKey(), application); }
        @Override public void publish(String appKey, long version) {
            var current = applications.get(appKey);
            applications.put(appKey, new Application(current.appKey(), current.name(), ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
