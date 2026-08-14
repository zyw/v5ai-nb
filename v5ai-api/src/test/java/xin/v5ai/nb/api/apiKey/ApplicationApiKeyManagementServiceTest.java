package xin.v5ai.nb.api.apiKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import xin.v5ai.nb.infrastructure.apiKey.ApplicationApiKeyStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationApiKeyManagementServiceTest {
    @Test
    void createApiKeyStoresBcryptHashAndReturnsRawKeyOnce() {
        var store = new RecordingApiKeyStore();
        var service = new ApplicationApiKeyManagementService(store, new BCryptPasswordEncoder());

        var result = service.createApiKey("demo");

        assertThat(result.apiKey()).startsWith("v5ai_");
        assertThat(result.apiKey()).hasSize(5 + 32);
        assertThat(store.hashes.get("demo")).isNotEqualTo(result.apiKey());
        assertThat(new BCryptPasswordEncoder().matches(result.apiKey(), store.hashes.get("demo"))).isTrue();
    }

    @Test
    void createApiKeyGeneratesDistinctKeys() {
        var store = new RecordingApiKeyStore();
        var service = new ApplicationApiKeyManagementService(store, new BCryptPasswordEncoder());

        var first = service.createApiKey("demo");
        var second = service.createApiKey("demo");

        assertThat(first.apiKey()).isNotEqualTo(second.apiKey());
    }

    private static final class RecordingApiKeyStore implements ApplicationApiKeyStore {
        private final java.util.Map<String, String> hashes = new java.util.HashMap<>();

        @Override
        public String findSecretHash(String appKey) {
            return hashes.get(appKey);
        }

        @Override
        public void upsertSecret(String appKey, String secretHash) {
            hashes.put(appKey, secretHash);
        }

        @Override
        public List<ApiKeyRecord> list() {
            return new ArrayList<>();
        }

        @Override
        public void delete(String appKey) {
            hashes.remove(appKey);
        }
    }
}
