package xin.v5ai.nb.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModelManagementServiceTest {
    @Test
    void modelCredentialsAreEncryptedBeforePersistence() {
        ModelRepository repository = mock(ModelRepository.class);
        CredentialCipher cipher = raw -> "ciphertext:" + raw;
        var service = new ModelManagementService(repository, cipher);

        service.createModel(new CreateModelCommand("openai", "gpt-4o", ModelType.CHAT, "secret-key"));

        verify(repository).save(argThat(model -> model.credentialsCiphertext().equals("ciphertext:secret-key")));
        assertThat(cipher.encrypt("secret-key")).isNotEqualTo("secret-key");
    }
}
