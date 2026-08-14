package xin.v5ai.nb.api.application;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.ApplicationVersion;
import xin.v5ai.nb.application.ApplicationManagementService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationControllerTest {
    @Test
    void createsListsAndPublishesApplication() {
        var repository = new RecordingApplicationRepository();
        var controller = new ApplicationController(new ApplicationManagementService(repository, modelId -> modelId == 7L));

        var created = controller.createApplication(new CreateApplicationRequest("demo", "Demo", "desc", 7L));
        var listed = controller.listApplications();
        controller.publishApplication("demo", new PublishApplicationRequest("release"));

        assertThat(created.appKey()).isEqualTo("demo");
        assertThat(created.description()).isEqualTo("desc");
        assertThat(listed).extracting(ApplicationResponse::appKey).containsExactly("demo");
        assertThat(repository.findByAppKey("demo").status()).isEqualTo(ApplicationStatus.PUBLISHED);
        assertThat(repository.versions).hasSize(1);
    }

    @Test
    void updatesDisablesAndListsVersions() {
        var repository = new RecordingApplicationRepository();
        var controller = new ApplicationController(new ApplicationManagementService(repository, modelId -> true));
        controller.createApplication(new CreateApplicationRequest("demo", "Demo", null, 7L));

        var updated = controller.updateApplication("demo", new UpdateApplicationRequest("Demo v2", "new", 8L));
        var versions = controller.listVersions("demo");
        controller.disableApplication("demo");

        assertThat(updated.name()).isEqualTo("Demo v2");
        assertThat(versions).isEmpty();
        assertThat(repository.findByAppKey("demo").status()).isEqualTo(ApplicationStatus.DISABLED);
    }

    private static final class RecordingApplicationRepository implements ApplicationRepository {
        private final List<Application> applications = new ArrayList<>();
        private final List<ApplicationVersion> versions = new ArrayList<>();

        @Override
        public Application findByAppKey(String appKey) {
            return applications.stream().filter(app -> app.appKey().equals(appKey)).findFirst().orElse(null);
        }

        @Override
        public void save(Application application) {
            applications.add(application);
        }

        @Override
        public void update(Application application) {
            applications.removeIf(app -> app.appKey().equals(application.appKey()));
            applications.add(application);
        }

        @Override
        public void disable(String appKey) {
            var current = findByAppKey(appKey);
            applications.remove(current);
            applications.add(new Application(current.appKey(), current.name(), current.description(),
                    ApplicationStatus.DISABLED, current.modelId(), current.publishedVersion()));
        }

        @Override
        public List<Application> list() {
            return applications;
        }

        @Override
        public long nextVersion(String appKey) {
            return versions.size() + 1L;
        }

        @Override
        public void saveVersion(ApplicationVersion version) {
            versions.add(version);
        }

        @Override
        public List<ApplicationVersion> listVersions(String appKey) {
            return versions.stream().filter(version -> version.appKey().equals(appKey)).toList();
        }

        @Override
        public void publish(String appKey, long version) {
            var current = findByAppKey(appKey);
            applications.remove(current);
            applications.add(new Application(appKey, current.name(), current.description(), ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
