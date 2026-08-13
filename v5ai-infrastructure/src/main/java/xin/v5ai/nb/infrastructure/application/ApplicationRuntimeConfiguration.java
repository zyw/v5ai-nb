package xin.v5ai.nb.infrastructure.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.PublishedApplicationResolver;

@Configuration
public class ApplicationRuntimeConfiguration {
    @Bean
    ApplicationRepository applicationRepository(
            @Value("${v5ai.runtime.dev-app-key:demo}") String appKey
    ) {
        var repository = new InMemoryApplicationRepository();
        repository.save(new Application(appKey, "Demo", ApplicationStatus.PUBLISHED, 1L, 1L));
        return repository;
    }

    @Bean
    PublishedApplicationResolver publishedApplicationResolver(ApplicationRepository applicationRepository) {
        return new PublishedApplicationResolver(applicationRepository);
    }

    private static final class InMemoryApplicationRepository implements ApplicationRepository {
        private final Map<String, Application> applications = new ConcurrentHashMap<>();

        @Override
        public Application findByAppKey(String appKey) {
            return applications.get(appKey);
        }

        @Override
        public void save(Application application) {
            applications.put(application.appKey(), application);
        }

        @Override
        public void publish(String appKey, long version) {
            var current = applications.get(appKey);
            applications.put(appKey, new Application(current.appKey(), current.name(), ApplicationStatus.PUBLISHED, current.modelId(), version));
        }
    }
}
