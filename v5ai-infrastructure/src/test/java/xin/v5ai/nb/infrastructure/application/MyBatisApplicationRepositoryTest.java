package xin.v5ai.nb.infrastructure.application;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.ApplicationVersion;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisApplicationRepositoryTest {
    @Test
    void savesAndFindsApplication() {
        var appMapper = new RecordingApplicationMapper();
        var versionMapper = new RecordingApplicationVersionMapper();
        var repository = new MyBatisApplicationRepository(appMapper, versionMapper);

        repository.save(new Application("demo", "Demo", ApplicationStatus.DRAFT, 7L, null));

        Application application = repository.findByAppKey("demo");

        assertThat(application.appKey()).isEqualTo("demo");
        assertThat(application.modelId()).isEqualTo(7L);
        assertThat(application.status()).isEqualTo(ApplicationStatus.DRAFT);
    }

    @Test
    void storesPublishedVersionSnapshotAndPublishesApp() {
        var appMapper = new RecordingApplicationMapper();
        var versionMapper = new RecordingApplicationVersionMapper();
        var repository = new MyBatisApplicationRepository(appMapper, versionMapper);
        repository.save(new Application("demo", "Demo", ApplicationStatus.DRAFT, 7L, null));

        long next = repository.nextVersion("demo");
        repository.saveVersion(new ApplicationVersion("demo", next, "{\"appKey\":\"demo\"}", "release"));
        repository.publish("demo", next);

        assertThat(next).isEqualTo(1L);
        assertThat(versionMapper.versions).hasSize(1);
        assertThat(repository.findByAppKey("demo").status()).isEqualTo(ApplicationStatus.PUBLISHED);
        assertThat(repository.findByAppKey("demo").publishedVersion()).isEqualTo(1L);
    }

    private static final class RecordingApplicationMapper implements ApplicationGateway {
        private final List<ApplicationEntity> applications = new ArrayList<>();

        @Override
        public void insertApplication(ApplicationEntity entity) {
            applications.add(entity);
        }

        @Override
        public ApplicationEntity selectByAppKey(String appKey) {
            return applications.stream()
                    .filter(entity -> entity.getAppKey().equals(appKey))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ApplicationEntity> selectApplications() {
            return applications;
        }

        @Override
        public void updatePublished(String appKey, String status, Long publishedVersion) {
            var entity = selectByAppKey(appKey);
            entity.setStatus(status);
            entity.setPublishedVersion(publishedVersion);
        }
    }

    private static final class RecordingApplicationVersionMapper implements ApplicationVersionGateway {
        private final List<ApplicationVersionEntity> versions = new ArrayList<>();

        @Override
        public void insertVersion(ApplicationVersionEntity entity) {
            versions.add(entity);
        }

        @Override
        public long countVersions(String appKey) {
            return versions.stream().filter(version -> version.getAppKey().equals(appKey)).count();
        }
    }
}
