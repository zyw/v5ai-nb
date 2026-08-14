package xin.v5ai.nb.infrastructure.application;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.ApplicationVersion;
import xin.v5ai.nb.application.PublishedApplicationResolver;

@Configuration
public class ApplicationRuntimeConfiguration {
    @Bean
    ApplicationRepository applicationRepository(
            @Value("${v5ai.runtime.dev-app-key:demo}") String appKey
    ) {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application(appKey, "Demo", "Development demo application", ApplicationStatus.PUBLISHED, 1L, 1L));
        return repository;
    }

    @Bean
    PublishedApplicationResolver publishedApplicationResolver(ApplicationRepository applicationRepository) {
        return new PublishedApplicationResolver(applicationRepository);
    }

    private static final class InMemoryApplicationRepository implements ApplicationRepository {
        private final Map<String, Application> applications = new ConcurrentHashMap<>();
        private final List<ApplicationVersion> versions = new ArrayList<>();

        @Override
        public Application findByAppKey(String appKey) {
            return applications.get(appKey);
        }

        @Override
        public void save(Application application) {
            applications.put(application.appKey(), application);
        }

        @Override
        public void update(Application application) {
            applications.put(application.appKey(), application);
        }

        @Override
        public void disable(String appKey) {
            var current = applications.get(appKey);
            if (current != null) {
                applications.put(appKey, new Application(current.appKey(), current.name(), current.description(),
                        ApplicationStatus.DISABLED, current.modelId(), current.publishedVersion()));
            }
        }

        @Override
        public List<Application> list() {
            return new ArrayList<>(applications.values());
        }

        @Override
        public long nextVersion(String appKey) {
            return versions.stream().filter(version -> version.appKey().equals(appKey)).count() + 1;
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
            var current = applications.get(appKey);
            applications.put(appKey, new Application(current.appKey(), current.name(), current.description(),
                    ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
