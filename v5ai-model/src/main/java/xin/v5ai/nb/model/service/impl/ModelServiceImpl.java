package xin.v5ai.nb.model.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.model.*;
import xin.v5ai.nb.model.mapper.ModelProviderRepository;
import xin.v5ai.nb.model.service.IModelService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements IModelService {

    private final ModelRepository repository;
    private final ModelProviderRepository providerRepository;
    private final CredentialCipher cipher;
    private final ModelConnectionTester connectionTester;

    @Override
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

    @Override
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

    @Override
    public void deleteModel(Long id) {
        repository.delete(id);
    }

    @Override
    public ModelConfig findModel(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<ModelConfig> listModels() {
        return repository.list();
    }

    @Override
    public List<ModelOption> listModelOptions() {
        return repository.listOptions();
    }

    @Override
    public ConnectionTestResult testConnection(Long modelId) {
        if (connectionTester == null) {
            return ConnectionTestResult.failure("model connection testing is not configured");
        }
        return connectionTester.test(modelId);
    }
}
