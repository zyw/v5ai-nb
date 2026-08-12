package xin.v5ai.nb.application;

public class PublishedApplicationResolver {
    private final ApplicationRepository repository;
    public PublishedApplicationResolver(ApplicationRepository repository) { this.repository = repository; }
    public Application resolve(String appKey) {
        var application = repository.findByAppKey(appKey);
        if (application == null || application.status() != ApplicationStatus.PUBLISHED) {
            throw new ApplicationPublishException("application is not published");
        }
        return application;
    }
}
