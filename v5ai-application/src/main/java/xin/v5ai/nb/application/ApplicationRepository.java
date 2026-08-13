package xin.v5ai.nb.application;
import java.util.List;

public interface ApplicationRepository {
    Application findByAppKey(String appKey);
    void save(Application application);
    List<Application> list();
    long nextVersion(String appKey);
    void saveVersion(ApplicationVersion version);
    void publish(String appKey, long version);
}
