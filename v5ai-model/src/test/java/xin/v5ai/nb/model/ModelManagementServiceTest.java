package xin.v5ai.nb.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelManagementServiceTest {
    @Test
    void createsProviderWhenKeyIsUnique() {
        var providers = new RecordingModelProviderRepository();
        var service = new ModelManagementService(new RecordingModelRepository(), providers, raw -> "ciphertext");

        ModelProvider provider = service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));

        assertThat(provider.providerKey()).isEqualTo("openai");
        assertThat(provider.name()).isEqualTo("OpenAI");
        assertThat(provider.enabled()).isTrue();
        assertThat(providers.saved).isEqualTo(provider);
    }

    @Test
    void duplicateProviderKeyIsRejected() {
        var providers = new RecordingModelProviderRepository();
        providers.save(new ModelProvider("openai", "OpenAI", "OPENAI", true));
        var service = new ModelManagementService(new RecordingModelRepository(), providers, raw -> "ciphertext");

        assertThatThrownBy(() -> service.createProvider(new CreateProviderCommand("openai", "Other", "OPENAI", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider key already exists");
    }

    @Test
    void modelCredentialsAreEncryptedBeforePersistence() {
        var repository = new RecordingModelRepository();
        var providers = new RecordingModelProviderRepository();
        providers.save(new ModelProvider("openai", "OpenAI", "OPENAI", true));
        CredentialCipher cipher = raw -> "ciphertext:" + raw;
        var service = new ModelManagementService(repository, providers, cipher);

        ModelConfig model = service.createModel(new CreateModelCommand("openai", "gpt-4o", ModelType.CHAT, "secret-key"));

        assertThat(model.modelKey()).isEqualTo("gpt-4o");
        assertThat(repository.saved.credentialsCiphertext()).isEqualTo("ciphertext:secret-key");
        assertThat(cipher.encrypt("secret-key")).isNotEqualTo("secret-key");
    }

    @Test
    void modelRequiresExistingEnabledProvider() {
        var providers = new RecordingModelProviderRepository();
        providers.save(new ModelProvider("openai", "OpenAI", "OPENAI", false));
        var service = new ModelManagementService(new RecordingModelRepository(), providers, raw -> "ciphertext");

        assertThatThrownBy(() -> service.createModel(new CreateModelCommand("openai", "gpt-4o", ModelType.CHAT, "secret-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider is not enabled");
    }

    private static final class RecordingModelRepository implements ModelRepository {
        private ModelConfig saved;
        private final List<ModelConfig> models = new ArrayList<>();

        @Override
        public void save(ModelConfig config) {
            saved = config;
            models.add(config);
        }

        @Override
        public List<ModelConfig> list() {
            return models;
        }
    }

    private static final class RecordingModelProviderRepository implements ModelProviderRepository {
        private ModelProvider saved;
        private final List<ModelProvider> providers = new ArrayList<>();

        @Override
        public void save(ModelProvider provider) {
            saved = provider;
            providers.add(provider);
        }

        @Override
        public ModelProvider findByKey(String providerKey) {
            return providers.stream()
                    .filter(provider -> provider.providerKey().equals(providerKey))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ModelProvider> list() {
            return providers;
        }
    }
}
