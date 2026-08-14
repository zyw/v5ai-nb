package xin.v5ai.nb.infrastructure.application;

import java.util.List;

public interface ApplicationVersionGateway {
    void insertVersion(ApplicationVersionEntity entity);

    long countVersions(String appKey);

    List<ApplicationVersionEntity> selectVersions(String appKey);
}
