package xin.v5ai.nb.infrastructure.apiKey;

import java.util.List;

public interface ApiKeyGateway {
    void upsert(ApiKeyEntity entity);

    ApiKeyEntity selectByAgentKey(String agentKey);

    List<ApiKeyEntity> selectAll();

    void deleteByAgentKey(String agentKey);
}
