package xin.v5ai.nb.application;

import org.springframework.stereotype.Service;
import java.util.function.Predicate;

@Service
public class ApplicationManagementService {
    private final ApplicationRepository repository;
    private final Predicate<Long> modelEnabled;
    public ApplicationManagementService(ApplicationRepository repository, Predicate<Long> modelEnabled) {
        this.repository = repository; this.modelEnabled = modelEnabled;
    }
    public void publish(String appKey) {
        var application = repository.findByAppKey(appKey);
        if (application == null || application.modelId() == null || application.publishedVersion() == null || !modelEnabled.test(application.modelId())) {
            throw new ApplicationPublishException("application requires an enabled model before publishing");
        }
        repository.publish(appKey, application.publishedVersion());
    }
}
