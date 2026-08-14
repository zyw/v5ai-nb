package xin.v5ai.nb.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationManagementServiceTest {
    @Test
    void createsDraftApplicationWithModel() {
        var repository = new InMemoryApplicationRepository();
        var service = new ApplicationManagementService(repository, modelId -> true);

        var application = service.createApplication(new CreateApplicationCommand("demo", "Demo", 7L));

        assertThat(application.appKey()).isEqualTo("demo");
        assertThat(application.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(repository.findByAppKey("demo")).isEqualTo(application);
    }

    @Test
    void duplicateAppKeyIsRejected() {
        var repository = new InMemoryApplicationRepository();
        var service = new ApplicationManagementService(repository, modelId -> true);
        service.createApplication(new CreateApplicationCommand("demo", "Demo", 7L));

        assertThatThrownBy(() -> service.createApplication(new CreateApplicationCommand("demo", "Other", 8L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void listsApplications() {
        var repository = new InMemoryApplicationRepository();
        var service = new ApplicationManagementService(repository, modelId -> true);
        service.createApplication(new CreateApplicationCommand("demo", "Demo", 7L));

        assertThat(service.listApplications())
                .extracting(Application::appKey)
                .containsExactly("demo");
    }

    @Test
    void updateApplicationKeepsStatusAndChangesNameAndModel() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", "old description", ApplicationStatus.DRAFT, 7L, null));
        var service = new ApplicationManagementService(repository, modelId -> true);

        var updated = service.updateApplication(new UpdateApplicationCommand("demo", "Demo v2", "new description", 8L));

        assertThat(updated.name()).isEqualTo("Demo v2");
        assertThat(updated.description()).isEqualTo("new description");
        assertThat(updated.modelId()).isEqualTo(8L);
        assertThat(updated.status()).isEqualTo(ApplicationStatus.DRAFT);
    }

    @Test
    void disableApplicationSetsDisabledStatus() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 7L, 1L));
        var service = new ApplicationManagementService(repository, modelId -> true);

        service.disable("demo");

        assertThat(repository.findByAppKey("demo").status()).isEqualTo(ApplicationStatus.DISABLED);
    }

    @Test
    void listVersionsReturnsSavedVersions() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 7L, 1L));
        repository.saveVersion(new ApplicationVersion("demo", 1L, "{}", "first"));
        var service = new ApplicationManagementService(repository, modelId -> true);

        assertThat(service.listVersions("demo")).extracting(ApplicationVersion::version).containsExactly(1L);
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
        repository.save(new Application("demo", "Demo", null, ApplicationStatus.DRAFT, 7L, null));
        var service = new ApplicationManagementService(repository, modelId -> modelId == 7L);

        service.publish(new PublishApplicationCommand("demo", "first release"));

        var application = repository.findByAppKey("demo");
        assertThat(application.status()).isEqualTo(ApplicationStatus.PUBLISHED);
        assertThat(application.publishedVersion()).isEqualTo(1L);
        assertThat(repository.versions).hasSize(1);
        assertThat(repository.versions.getFirst().snapshotJson())
                .contains("\"appKey\":\"demo\"")
                .contains("\"modelId\":7");
    }

    @Test
    void publishedApplicationCanBeResolved() {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 7L, 1L));
        var resolver = new PublishedApplicationResolver(repository);

        var application = resolver.resolve("demo");

        assertThat(application.status()).isEqualTo(ApplicationStatus.PUBLISHED);
    }

    private static final class InMemoryApplicationRepository implements ApplicationRepository {
        private final java.util.Map<String, Application> applications = new java.util.HashMap<>();
        private final List<ApplicationVersion> versions = new ArrayList<>();

        @Override public Application findByAppKey(String appKey) { return applications.get(appKey); }
        @Override public void save(Application application) { applications.put(application.appKey(), application); }
        @Override public void update(Application application) { applications.put(application.appKey(), application); }
        @Override public void disable(String appKey) {
            var current = applications.get(appKey);
            if (current != null) {
                applications.put(appKey, new Application(current.appKey(), current.name(), current.description(),
                        ApplicationStatus.DISABLED, current.modelId(), current.publishedVersion()));
            }
        }
        @Override public List<Application> list() { return new ArrayList<>(applications.values()); }
        @Override public long nextVersion(String appKey) { return versions.stream().filter(v -> v.appKey().equals(appKey)).count() + 1; }
        @Override public void saveVersion(ApplicationVersion version) { versions.add(version); }
        @Override public List<ApplicationVersion> listVersions(String appKey) {
            return versions.stream().filter(v -> v.appKey().equals(appKey)).toList();
        }
        @Override public void publish(String appKey, long version) {
            var current = applications.get(appKey);
            applications.put(appKey, new Application(current.appKey(), current.name(), current.description(),
                    ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
