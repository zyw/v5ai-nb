package xin.v5ai.nb.infrastructure.application;

import java.util.List;

public interface ApplicationGateway {
    void insertApplication(ApplicationEntity entity);

    void updateApplication(ApplicationEntity entity);

    void disable(String appKey);

    ApplicationEntity selectByAppKey(String appKey);

    List<ApplicationEntity> selectApplications();

    void updatePublished(String appKey, String status, Long publishedVersion);
}
