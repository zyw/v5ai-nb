package xin.v5ai.nb.infrastructure.apiKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptApplicationApiKeyVerifierTest {
    @Test
    void verifiesApiKeyAgainstStoredHashAndAppKey() {
        var encoder = new BCryptPasswordEncoder();
        var store = new InMemoryApplicationApiKeyStore("demo", encoder.encode("secret"));
        var verifier = new BcryptApplicationApiKeyVerifier(store, encoder);

        assertThat(verifier.verify("demo", "secret")).isTrue();
        assertThat(verifier.verify("demo", "wrong")).isFalse();
        assertThat(verifier.verify("other", "secret")).isFalse();
    }

    private static final class InMemoryApplicationApiKeyStore implements ApplicationApiKeyStore {
        private final String appKey;
        private final String secretHash;

        private InMemoryApplicationApiKeyStore(String appKey, String secretHash) {
            this.appKey = appKey;
            this.secretHash = secretHash;
        }

        @Override
        public String findSecretHash(String appKey) {
            return this.appKey.equals(appKey) ? secretHash : null;
        }

        @Override
        public void upsertSecret(String appKey, String secretHash) {
        }

        @Override
        public List<ApiKeyRecord> list() {
            return List.of();
        }

        @Override
        public void delete(String appKey) {
        }
    }
}
