package xin.v5ai.nb.infrastructure.apiKey;

public interface ApplicationApiKeyStore {
    String findSecretHash(String appKey);
}
