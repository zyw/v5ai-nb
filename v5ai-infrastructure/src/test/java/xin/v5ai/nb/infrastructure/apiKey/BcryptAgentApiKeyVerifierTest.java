package xin.v5ai.nb.infrastructure.apiKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptAgentApiKeyVerifierTest {
    @Test
    void verifiesApiKeyAgainstStoredHashAndAgentKey() {
        var encoder = new BCryptPasswordEncoder();
        var store = new InMemoryAgentApiKeyStore("demo", encoder.encode("secret"));
        var verifier = new BcryptAgentApiKeyVerifier(store, encoder);

        assertThat(verifier.verify("demo", "secret")).isTrue();
        assertThat(verifier.verify("demo", "wrong")).isFalse();
        assertThat(verifier.verify("other", "secret")).isFalse();
    }

    private static final class InMemoryAgentApiKeyStore implements AgentApiKeyStore {
        private final String agentKey;
        private final String secretHash;

        private InMemoryAgentApiKeyStore(String agentKey, String secretHash) {
            this.agentKey = agentKey;
            this.secretHash = secretHash;
        }

        @Override
        public String findSecretHash(String agentKey) {
            return this.agentKey.equals(agentKey) ? secretHash : null;
        }

        @Override
        public void upsertSecret(String agentKey, String secretHash) {
        }

        @Override
        public List<ApiKeyRecord> list() {
            return List.of();
        }

        @Override
        public void delete(String agentKey) {
        }
    }
}
