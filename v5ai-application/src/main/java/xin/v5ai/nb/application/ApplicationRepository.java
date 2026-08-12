package xin.v5ai.nb.application;
public interface ApplicationRepository {
    Application findByAppKey(String appKey);
    void save(Application application);
    void publish(String appKey, long version);
}
