package xin.v5ai.nb.infrastructure.apiKey;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Primary
@Repository
public class MyBatisAgentApiKeyStore implements AgentApiKeyStore {
    private final ApiKeyGateway gateway;

    public MyBatisAgentApiKeyStore(ApiKeyGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String findSecretHash(String agentKey) {
        var entity = gateway.selectByAgentKey(agentKey);
        return entity == null || !Boolean.TRUE.equals(entity.getEnabled()) ? null : entity.getSecretHash();
    }

    @Override
    public void upsertSecret(String agentKey, String secretHash) {
        var entity = new ApiKeyEntity();
        entity.setAgentKey(agentKey);
        entity.setSecretHash(secretHash);
        entity.setEnabled(true);
        gateway.upsert(entity);
    }

    @Override
    public List<ApiKeyRecord> list() {
        return gateway.selectAll().stream()
                .map(entity -> new ApiKeyRecord(entity.getAgentKey(), Boolean.TRUE.equals(entity.getEnabled())))
                .toList();
    }

    @Override
    public void delete(String agentKey) {
        gateway.deleteByAgentKey(agentKey);
    }
}
