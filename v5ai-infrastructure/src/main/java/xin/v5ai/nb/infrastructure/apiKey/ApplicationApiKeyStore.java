package xin.v5ai.nb.infrastructure.apiKey;

import java.util.List;

public interface ApplicationApiKeyStore {
    String findSecretHash(String appKey);

    void upsertSecret(String appKey, String secretHash);

    List<ApiKeyRecord> list();

    void delete(String appKey);

    record ApiKeyRecord(String appKey, boolean enabled) {
    }
}
