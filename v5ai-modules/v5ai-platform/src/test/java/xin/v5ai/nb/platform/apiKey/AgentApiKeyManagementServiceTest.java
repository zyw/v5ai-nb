package xin.v5ai.nb.platform.apiKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentApiKeyManagementServiceTest {
    @Test
    void createApiKeyStoresBcryptHashAndReturnsRawKeyOnce() {
        var store = new RecordingApiKeyStore();
        var service = new AgentApiKeyManagementService(store, new BCryptPasswordEncoder());

        var result = service.createApiKey("demo");

        assertThat(result.apiKey()).startsWith("v5ai_");
        assertThat(result.apiKey()).hasSize(5 + 32);
        assertThat(store.hashes.get("demo")).isNotEqualTo(result.apiKey());
        assertThat(new BCryptPasswordEncoder().matches(result.apiKey(), store.hashes.get("demo"))).isTrue();
    }

    @Test
    void createApiKeyGeneratesDistinctKeys() {
        var store = new RecordingApiKeyStore();
        var service = new AgentApiKeyManagementService(store, new BCryptPasswordEncoder());

        var first = service.createApiKey("demo");
        var second = service.createApiKey("demo");

        assertThat(first.apiKey()).isNotEqualTo(second.apiKey());
    }

    private static final class RecordingApiKeyStore implements AgentApiKeyStore {
        private final java.util.Map<String, String> hashes = new java.util.HashMap<>();

        @Override
        public String findSecretHash(String agentKey) {
            return hashes.get(agentKey);
        }

        @Override
        public void upsertSecret(String agentKey, String secretHash) {
            hashes.put(agentKey, secretHash);
        }

        @Override
        public List<ApiKeyRecord> list() {
            return new ArrayList<>();
        }

        @Override
        public void delete(String agentKey) {
            hashes.remove(agentKey);
        }
    }
}
