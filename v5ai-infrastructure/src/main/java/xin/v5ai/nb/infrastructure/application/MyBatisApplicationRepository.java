package xin.v5ai.nb.infrastructure.application;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.ApplicationVersion;

import java.util.List;

@Primary
@Repository
public class MyBatisApplicationRepository implements ApplicationRepository {
    private final ApplicationGateway applicationGateway;
    private final ApplicationVersionGateway versionGateway;

    public MyBatisApplicationRepository(ApplicationGateway applicationGateway, ApplicationVersionGateway versionGateway) {
        this.applicationGateway = applicationGateway;
        this.versionGateway = versionGateway;
    }

    @Override
    public Application findByAppKey(String appKey) {
        return toDomain(applicationGateway.selectByAppKey(appKey));
    }

    @Override
    public void save(Application application) {
        applicationGateway.insertApplication(toEntity(application));
    }

    @Override
    public List<Application> list() {
        return applicationGateway.selectApplications().stream()
                .map(MyBatisApplicationRepository::toDomain)
                .toList();
    }

    @Override
    public long nextVersion(String appKey) {
        return versionGateway.countVersions(appKey) + 1;
    }

    @Override
    public void saveVersion(ApplicationVersion version) {
        var entity = new ApplicationVersionEntity();
        entity.setAppKey(version.appKey());
        entity.setVersion(version.version());
        entity.setSnapshotJson(version.snapshotJson());
        entity.setDescription(version.description());
        versionGateway.insertVersion(entity);
    }

    @Override
    public void publish(String appKey, long version) {
        applicationGateway.updatePublished(appKey, ApplicationStatus.PUBLISHED.name(), version);
    }

    private static ApplicationEntity toEntity(Application application) {
        var entity = new ApplicationEntity();
        entity.setAppKey(application.appKey());
        entity.setName(application.name());
        entity.setStatus(application.status().name());
        entity.setModelId(application.modelId());
        entity.setPublishedVersion(application.publishedVersion());
        return entity;
    }

    private static Application toDomain(ApplicationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Application(entity.getAppKey(), entity.getName(), ApplicationStatus.valueOf(entity.getStatus()),
                entity.getModelId(), entity.getPublishedVersion());
    }
}
