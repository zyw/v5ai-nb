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

        var created = controller.createApplication(new CreateApplicationRequest("demo", "Demo", 7L));
        var listed = controller.listApplications();
        controller.publishApplication("demo", new PublishApplicationRequest("release"));

        assertThat(created.appKey()).isEqualTo("demo");
        assertThat(listed).extracting(ApplicationResponse::appKey).containsExactly("demo");
        assertThat(repository.findByAppKey("demo").status()).isEqualTo(ApplicationStatus.PUBLISHED);
        assertThat(repository.versions).hasSize(1);
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
        public void publish(String appKey, long version) {
            var current = findByAppKey(appKey);
            applications.remove(current);
            applications.add(new Application(appKey, current.name(), ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
