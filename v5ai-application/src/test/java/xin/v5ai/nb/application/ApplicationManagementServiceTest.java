package xin.v5ai.nb.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationManagementServiceTest {
    @Test
    void createsDraftApplicationWithModel() {
        var repository = new InMemoryApplicationRepository();
        var service = new ApplicationManagementService(repository, modelId -> true);

        var application = service.createApplication(new CreateApplicationCommand("demo", "Demo", 7L));

        org.assertj.core.api.Assertions.assertThat(application.appKey()).isEqualTo("demo");
        org.assertj.core.api.Assertions.assertThat(application.status()).isEqualTo(ApplicationStatus.DRAFT);
        org.assertj.core.api.Assertions.assertThat(repository.findByAppKey("demo")).isEqualTo(application);
    }

    @Test
    void listsApplications() {
        var repository = new InMemoryApplicationRepository();
        var service = new ApplicationManagementService(repository, modelId -> true);
        service.createApplication(new CreateApplicationCommand("demo", "Demo", 7L));

        org.assertj.core.api.Assertions.assertThat(service.listApplications())
                .extracting(Application::appKey)
                .containsExactly("demo");
    }

    @Test
    void publishingApplicationWithoutModelIsRejected() {
        var service = new ApplicationManagementService(new InMemoryApplicationRepository(), modelId -> false);

        assertThatThrownBy(() -> service.publish("demo"))
                .isInstanceOf(ApplicationPublishException.class);
    }

    @Test
    void publishingApplicationUsesModelIdAndPublishesVersion() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", ApplicationStatus.DRAFT, 7L, null));
        var service = new ApplicationManagementService(repository, modelId -> modelId == 7L);

        service.publish(new PublishApplicationCommand("demo", "first release"));

        var application = repository.findByAppKey("demo");
        org.assertj.core.api.Assertions.assertThat(application.status()).isEqualTo(ApplicationStatus.PUBLISHED);
        org.assertj.core.api.Assertions.assertThat(application.publishedVersion()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(repository.versions).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(repository.versions.getFirst().snapshotJson())
                .contains("\"appKey\":\"demo\"")
                .contains("\"modelId\":7");
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
        private final List<ApplicationVersion> versions = new ArrayList<>();

        @Override public Application findByAppKey(String appKey) { return applications.get(appKey); }
        @Override public void save(Application application) { applications.put(application.appKey(), application); }
        @Override public List<Application> list() { return new ArrayList<>(applications.values()); }
        @Override public long nextVersion(String appKey) { return versions.stream().filter(v -> v.appKey().equals(appKey)).count() + 1; }
        @Override public void saveVersion(ApplicationVersion version) { versions.add(version); }
        @Override public void publish(String appKey, long version) {
            var current = applications.get(appKey);
            applications.put(appKey, new Application(current.appKey(), current.name(), ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
