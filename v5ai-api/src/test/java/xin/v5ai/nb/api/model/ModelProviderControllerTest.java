package xin.v5ai.nb.api.model;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.model.CreateModelCommand;
import xin.v5ai.nb.model.CreateProviderCommand;
import xin.v5ai.nb.model.CredentialCipher;
import xin.v5ai.nb.model.ModelConfig;
import xin.v5ai.nb.model.ModelManagementService;
import xin.v5ai.nb.model.ModelProvider;
import xin.v5ai.nb.model.ModelProviderRepository;
import xin.v5ai.nb.model.ModelRepository;
import xin.v5ai.nb.model.ModelType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProviderControllerTest {
    @Test
    void createsAndListsProviders() {
        var service = service();
        var controller = new ModelProviderController(service);

        var created = controller.createProvider(new CreateProviderRequest("openai", "OpenAI", "OPENAI", true));
        var providers = controller.listProviders();

        assertThat(created.providerKey()).isEqualTo("openai");
        assertThat(providers).extracting(ModelProviderResponse::providerKey).containsExactly("openai");
    }

    @Test
    void createsAndListsModels() {
        var service = service();
        service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));
        var controller = new ModelController(service);

        var created = controller.createModel(new CreateModelRequest("openai", "gpt-4o", ModelType.CHAT, "secret"));
        var models = controller.listModels();

        assertThat(created.modelKey()).isEqualTo("gpt-4o");
        assertThat(models).extracting(ModelResponse::modelKey).containsExactly("gpt-4o");
        assertThat(models.getFirst().credentialsCiphertext()).isEqualTo("ciphertext:secret");
    }

    private static ModelManagementService service() {
        return new ModelManagementService(new RecordingModelRepository(), new RecordingProviderRepository(),
                (CredentialCipher) raw -> "ciphertext:" + raw);
    }

    private static final class RecordingModelRepository implements ModelRepository {
        private final List<ModelConfig> models = new ArrayList<>();

        @Override
        public void save(ModelConfig model) {
            models.add(model);
        }

        @Override
        public List<ModelConfig> list() {
            return models;
        }
    }

    private static final class RecordingProviderRepository implements ModelProviderRepository {
        private final List<ModelProvider> providers = new ArrayList<>();

        @Override
        public void save(ModelProvider provider) {
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
