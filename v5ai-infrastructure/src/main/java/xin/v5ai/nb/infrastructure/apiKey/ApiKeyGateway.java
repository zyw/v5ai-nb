package xin.v5ai.nb.infrastructure.apiKey;

import java.util.List;

public interface ApiKeyGateway {
    void upsert(ApiKeyEntity entity);

    ApiKeyEntity selectByAppKey(String appKey);

    List<ApiKeyEntity> selectAll();

    void deleteByAppKey(String appKey);
}
