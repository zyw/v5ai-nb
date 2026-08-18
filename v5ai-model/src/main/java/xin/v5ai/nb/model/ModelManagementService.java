package xin.v5ai.nb.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;

import java.util.List;

@Service
public class ModelManagementService {
    private final ModelRepository repository;
    private final ModelProviderRepository providerRepository;
    private final CredentialCipher cipher;
    private final ModelConnectionTester connectionTester;

    public ModelManagementService(ModelRepository repository, CredentialCipher cipher) {
        this(repository, null, cipher, null);
    }

    public ModelManagementService(ModelRepository repository, ModelProviderRepository providerRepository, CredentialCipher cipher) {
        this(repository, providerRepository, cipher, null);
    }

    @Autowired
    public ModelManagementService(
            ModelRepository repository,
            ModelProviderRepository providerRepository,
            CredentialCipher cipher,
            ModelConnectionTester connectionTester
    ) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.cipher = cipher;
        this.connectionTester = connectionTester;
    }

    public ModelProvider createProvider(CreateProviderCommand command) {
        if (command.providerKey() == null || command.providerKey().isBlank()
                || command.name() == null || command.name().isBlank()
                || command.providerType() == null || command.providerType().isBlank()) {
            throw new IllegalArgumentException("provider key, name and type are required");
        }
        if (providerRepository != null && providerRepository.findByKey(command.providerKey()) != null) {
            throw new IllegalArgumentException("provider key already exists");
        }
        var provider = new ModelProvider(command.providerKey(), command.name(), command.providerType(), command.enabled());
        if (providerRepository != null) {
            providerRepository.save(provider);
        }
        return provider;
    }

    public ModelProvider updateProvider(UpdateProviderCommand command) {
        var existing = providerRepository == null ? null : providerRepository.findByKey(command.providerKey());
        if (existing == null) {
            throw new IllegalArgumentException("provider does not exist: " + command.providerKey());
        }
        var provider = new ModelProvider(existing.id(), command.providerKey(), command.name(), command.providerType(), command.enabled());
        providerRepository.update(provider);
        return provider;
    }

    public void deleteProvider(String providerKey) {
        if (providerRepository == null) {
            return;
        }
        boolean referenced = repository.list().stream()
                .anyMatch(model -> model.providerKey().equals(providerKey));
        if (referenced) {
            throw new IllegalArgumentException("provider has models and cannot be deleted");
        }
        providerRepository.delete(providerKey);
    }

    public List<ModelProvider> listProviders() {
        return providerRepository == null ? List.of() : providerRepository.list();
    }

    public PageResult<ModelProvider> queryPageList(PageQuery pageQuery) {
        // TODO implement queryPageList
        return null;
    }

    public ModelConfig createModel(CreateModelCommand command) {
        if (command.providerKey() == null || command.providerKey().isBlank()
                || command.modelKey() == null || command.modelKey().isBlank()
                || command.type() == null) {
            throw new IllegalArgumentException("provider, model and type are required");
        }
        if (providerRepository != null) {
            var provider = providerRepository.findByKey(command.providerKey());
            if (provider == null || !provider.enabled()) {
                throw new IllegalArgumentException("provider is not enabled");
            }
        }
        var model = new ModelConfig(command.providerKey(), command.modelKey(), command.type(),
                cipher.encrypt(command.credentials()));
        repository.save(model);
        return model;
    }

    public ModelConfig updateModel(UpdateModelCommand command) {
        var existing = repository.findById(command.id());
        if (existing == null) {
            throw new IllegalArgumentException("model does not exist: " + command.id());
        }
        var model = new ModelConfig(
                command.id(),
                existing.providerKey(),
                command.modelKey(),
                command.type(),
                command.credentials() == null ? existing.credentialsCiphertext() : cipher.encrypt(command.credentials()),
                command.enabled());
        repository.update(model);
        return model;
    }

    public void deleteModel(Long id) {
        repository.delete(id);
    }

    public ModelConfig findModel(Long id) {
        return repository.findById(id);
    }

    public List<ModelConfig> listModels() {
        return repository.list();
    }

    public List<ModelOption> listModelOptions() {
        return repository.listOptions();
    }

    public ConnectionTestResult testConnection(Long modelId) {
        if (connectionTester == null) {
            return ConnectionTestResult.failure("model connection testing is not configured");
        }
        return connectionTester.test(modelId);
    }
}
