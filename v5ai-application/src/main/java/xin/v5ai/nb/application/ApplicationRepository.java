package xin.v5ai.nb.application;

import java.util.List;

public interface ApplicationRepository {
    Application findByAppKey(String appKey);

    void save(Application application);

    void update(Application application);

    void disable(String appKey);

    List<Application> list();

    long nextVersion(String appKey);

    void saveVersion(ApplicationVersion version);

    List<ApplicationVersion> listVersions(String appKey);

    void publish(String appKey, long version);
}
