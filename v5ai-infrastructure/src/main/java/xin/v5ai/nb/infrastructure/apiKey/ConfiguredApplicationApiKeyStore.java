package xin.v5ai.nb.infrastructure.apiKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredApplicationApiKeyStore implements ApplicationApiKeyStore {
    private final String appKey;
    private final String secretHash;

    public ConfiguredApplicationApiKeyStore(
            @Value("${v5ai.runtime.dev-app-key:demo}") String appKey,
            @Value("${v5ai.runtime.dev-api-key-hash:}") String secretHash
    ) {
        this.appKey = appKey;
        this.secretHash = secretHash;
    }

    @Override
    public String findSecretHash(String appKey) {
        if (secretHash == null || secretHash.isBlank()) {
            return null;
        }
        return this.appKey.equals(appKey) ? secretHash : null;
    }
}
