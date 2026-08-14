package xin.v5ai.nb.application;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

@Service
public class ApplicationManagementService {
    private final ApplicationRepository repository;
    private final Predicate<Long> modelEnabled;

    public ApplicationManagementService(ApplicationRepository repository, Predicate<Long> modelEnabled) {
        this.repository = repository;
        this.modelEnabled = modelEnabled;
    }

    public Application createApplication(CreateApplicationCommand command) {
        if (command.appKey() == null || command.appKey().isBlank()
                || command.name() == null || command.name().isBlank()
                || command.modelId() == null) {
            throw new IllegalArgumentException("app key, name and model are required");
        }
        if (repository.findByAppKey(command.appKey()) != null) {
            throw new IllegalArgumentException("application already exists: " + command.appKey());
        }
        var application = new Application(command.appKey(), command.name(), command.description(),
                ApplicationStatus.DRAFT, command.modelId(), null);
        repository.save(application);
        return application;
    }

    public List<Application> listApplications() {
        return repository.list();
    }

    public Application updateApplication(UpdateApplicationCommand command) {
        var existing = repository.findByAppKey(command.appKey());
        if (existing == null) {
            throw new IllegalArgumentException("application does not exist: " + command.appKey());
        }
        var application = new Application(
                existing.appKey(),
                command.name() == null || command.name().isBlank() ? existing.name() : command.name(),
                command.description() == null ? existing.description() : command.description(),
                existing.status(),
                command.modelId() == null ? existing.modelId() : command.modelId(),
                existing.publishedVersion());
        repository.update(application);
        return application;
    }

    public void disable(String appKey) {
        if (repository.findByAppKey(appKey) == null) {
            throw new IllegalArgumentException("application does not exist: " + appKey);
        }
        repository.disable(appKey);
    }

    public List<ApplicationVersion> listVersions(String appKey) {
        if (repository.findByAppKey(appKey) == null) {
            throw new IllegalArgumentException("application does not exist: " + appKey);
        }
        return repository.listVersions(appKey);
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
                {"appKey":"%s","name":"%s","description":"%s","modelId":%d}
                """.formatted(
                        application.appKey(),
                        application.name(),
                        application.description() == null ? "" : application.description(),
                        application.modelId()).trim();
    }
}
