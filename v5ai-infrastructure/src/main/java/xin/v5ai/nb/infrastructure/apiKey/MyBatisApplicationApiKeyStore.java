package xin.v5ai.nb.infrastructure.apiKey;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Primary
@Repository
public class MyBatisApplicationApiKeyStore implements ApplicationApiKeyStore {
    private final ApiKeyGateway gateway;

    public MyBatisApplicationApiKeyStore(ApiKeyGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String findSecretHash(String appKey) {
        var entity = gateway.selectByAppKey(appKey);
        return entity == null || !Boolean.TRUE.equals(entity.getEnabled()) ? null : entity.getSecretHash();
    }

    @Override
    public void upsertSecret(String appKey, String secretHash) {
        var entity = new ApiKeyEntity();
        entity.setAppKey(appKey);
        entity.setSecretHash(secretHash);
        entity.setEnabled(true);
        gateway.upsert(entity);
    }

    @Override
    public List<ApiKeyRecord> list() {
        return gateway.selectAll().stream()
                .map(entity -> new ApiKeyRecord(entity.getAppKey(), Boolean.TRUE.equals(entity.getEnabled())))
                .toList();
    }

    @Override
    public void delete(String appKey) {
        gateway.deleteByAppKey(appKey);
    }
}
