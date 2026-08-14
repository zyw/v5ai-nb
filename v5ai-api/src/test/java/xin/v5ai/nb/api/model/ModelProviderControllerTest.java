package xin.v5ai.nb.api.model;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.application.Application;
import xin.v5ai.nb.application.ApplicationRepository;
import xin.v5ai.nb.application.ApplicationStatus;
import xin.v5ai.nb.application.ApplicationVersion;
import xin.v5ai.nb.common.exception.ErrorCode;
import xin.v5ai.nb.common.exception.V5aiException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void updatesAndDeletesProvider() {
        var service = service();
        service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));
        var controller = new ModelProviderController(service);

        var updated = controller.updateProvider("openai", new UpdateProviderRequest("OpenAI v2", "OPENAI", false));
        controller.deleteProvider("openai");

        assertThat(updated.name()).isEqualTo("OpenAI v2");
        assertThat(controller.listProviders()).isEmpty();
    }

    @Test
    void deleteProviderRejectsProviderStillUsedByModels() {
        var service = service();
        service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));
        service.createModel(new CreateModelCommand("openai", "gpt-4o", ModelType.CHAT, "secret"));
        var controller = new ModelProviderController(service);

        assertThatThrownBy(() -> controller.deleteProvider("openai"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has models");
    }

    @Test
    void createsAndListsModels() {
        var service = service();
        service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));
        var controller = new ModelController(service, new RecordingApplicationRepository());

        var created = controller.createModel(new CreateModelRequest("openai", "gpt-4o", ModelType.CHAT, "secret"));
        var models = controller.listModels();

        assertThat(created.modelKey()).isEqualTo("gpt-4o");
        assertThat(models).extracting(ModelResponse::modelKey).containsExactly("gpt-4o");
        assertThat(models.getFirst().credentialsCiphertext()).isEqualTo("ciphertext:secret");
    }

    @Test
    void updatesDeletesAndTestsModel() {
        var service = service();
        service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));
        var controller = new ModelController(service, new RecordingApplicationRepository());
        var created = controller.createModel(new CreateModelRequest("openai", "gpt-4o", ModelType.CHAT, "secret"));

        var updated = controller.updateModel(1L, new UpdateModelRequest("gpt-4o-mini", ModelType.CHAT, null, true));
        var testResult = controller.testConnection(1L);
        controller.deleteModel(1L);

        assertThat(updated.modelKey()).isEqualTo("gpt-4o-mini");
        assertThat(testResult.ok()).isFalse();
    }

    @Test
    void deleteModelRejectsModelInUseByApplication() {
        var service = service();
        service.createProvider(new CreateProviderCommand("openai", "OpenAI", "OPENAI", true));
        service.createModel(new CreateModelCommand("openai", "gpt-4o", ModelType.CHAT, "secret"));
        var applications = new RecordingApplicationRepository();
        applications.applications.add(new Application("demo", "Demo", null, ApplicationStatus.PUBLISHED, 1L, 1L));
        var controller = new ModelController(service, applications);

        assertThatThrownBy(() -> controller.deleteModel(1L))
                .isInstanceOf(V5aiException.class)
                .extracting(ex -> ((V5aiException) ex).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void deleteModelRejectsUnknownModel() {
        var service = service();
        var controller = new ModelController(service, new RecordingApplicationRepository());

        assertThatThrownBy(() -> controller.deleteModel(99L))
                .isInstanceOf(V5aiException.class)
                .extracting(ex -> ((V5aiException) ex).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
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
        public ModelConfig findById(Long id) {
            return models.isEmpty() ? null : models.getFirst();
        }

        @Override
        public void update(ModelConfig model) {
            models.removeIf(item -> Long.valueOf(1L).equals(item.id()));
            models.add(model);
        }

        @Override
        public void delete(Long id) {
            models.removeIf(item -> Long.valueOf(1L).equals(item.id()));
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
        public ModelProvider findById(Long id) {
            return providers.isEmpty() ? null : providers.getFirst();
        }

        @Override
        public ModelProvider findByKey(String providerKey) {
            return providers.stream()
                    .filter(provider -> provider.providerKey().equals(providerKey))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void update(ModelProvider provider) {
            providers.removeIf(item -> item.providerKey().equals(provider.providerKey()));
            providers.add(provider);
        }

        @Override
        public void delete(String providerKey) {
            providers.removeIf(item -> item.providerKey().equals(providerKey));
        }

        @Override
        public List<ModelProvider> list() {
            return providers;
        }
    }

    private static final class RecordingApplicationRepository implements ApplicationRepository {
        private final List<Application> applications = new ArrayList<>();

        @Override
        public Application findByAppKey(String appKey) {
            return applications.stream().filter(app -> app.appKey().equals(appKey)).findFirst().orElse(null);
        }

        @Override
        public void save(Application application) {
            applications.add(application);
        }

        @Override
        public void update(Application application) {
        }

        @Override
        public void disable(String appKey) {
        }

        @Override
        public List<Application> list() {
            return applications;
        }

        @Override
        public long nextVersion(String appKey) {
            return 1;
        }

        @Override
        public void saveVersion(ApplicationVersion version) {
        }

        @Override
        public List<ApplicationVersion> listVersions(String appKey) {
            return List.of();
        }

        @Override
        public void publish(String appKey, long version) {
        }
    }
}
