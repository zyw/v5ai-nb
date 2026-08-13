package xin.v5ai.nb.infrastructure.application;

public interface ApplicationVersionGateway {
    void insertVersion(ApplicationVersionEntity entity);
    long countVersions(String appKey);
}
