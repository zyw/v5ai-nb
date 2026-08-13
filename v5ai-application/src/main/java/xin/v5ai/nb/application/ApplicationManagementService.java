package xin.v5ai.nb.application;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.function.Predicate;

@Service
public class ApplicationManagementService {
    private final ApplicationRepository repository;
    private final Predicate<Long> modelEnabled;
    public ApplicationManagementService(ApplicationRepository repository, Predicate<Long> modelEnabled) {
        this.repository = repository; this.modelEnabled = modelEnabled;
    }

    public Application createApplication(CreateApplicationCommand command) {
        if (command.appKey() == null || command.appKey().isBlank()
                || command.name() == null || command.name().isBlank()
                || command.modelId() == null) {
            throw new IllegalArgumentException("app key, name and model are required");
        }
        var application = new Application(command.appKey(), command.name(), ApplicationStatus.DRAFT, command.modelId(), null);
        repository.save(application);
        return application;
    }

    public List<Application> listApplications() {
        return repository.list();
    }

    public void publish(String appKey) {
        publish(new PublishApplicationCommand(appKey, ""));
    }

    public void publish(PublishApplicationCommand command) {
        String appKey = command.appKey();
        var application = repository.findByAppKey(appKey);
        if (application == null || application.modelId() == null || !modelEnabled.test(application.modelId())) {
            throw new ApplicationPublishException("application requires an enabled model before publishing");
        }
        long version = repository.nextVersion(appKey);
        repository.saveVersion(new ApplicationVersion(appKey, version, snapshot(application), command.description()));
        repository.publish(appKey, version);
    }

    private String snapshot(Application application) {
        return """
                {"appKey":"%s","name":"%s","modelId":%d}
                """.formatted(application.appKey(), application.name(), application.modelId()).trim();
    }
}
