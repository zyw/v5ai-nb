package xin.v5ai.nb.model;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelManagementService {
    private final ModelRepository repository;
    private final ModelProviderRepository providerRepository;
    private final CredentialCipher cipher;

    public ModelManagementService(ModelRepository repository, CredentialCipher cipher) {
        this(repository, null, cipher);
    }

    public ModelManagementService(ModelRepository repository, ModelProviderRepository providerRepository, CredentialCipher cipher) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.cipher = cipher;
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

    public List<ModelProvider> listProviders() {
        return providerRepository == null ? List.of() : providerRepository.list();
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

    public List<ModelConfig> listModels() {
        return repository.list();
    }
}
