package xin.v5ai.nb.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelManagementServiceTest {
    @Test
    void modelCredentialsAreEncryptedBeforePersistence() {
        var repository = new RecordingModelRepository();
        CredentialCipher cipher = raw -> "ciphertext:" + raw;
        var service = new ModelManagementService(repository, cipher);

        service.createModel(new CreateModelCommand("openai", "gpt-4o", ModelType.CHAT, "secret-key"));

        assertThat(repository.saved.credentialsCiphertext()).isEqualTo("ciphertext:secret-key");
        assertThat(cipher.encrypt("secret-key")).isNotEqualTo("secret-key");
    }

    private static final class RecordingModelRepository implements ModelRepository {
        private ModelConfig saved;

        @Override
        public void save(ModelConfig config) {
            saved = config;
        }
    }
}
