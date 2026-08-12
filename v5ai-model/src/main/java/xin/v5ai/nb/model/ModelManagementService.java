package xin.v5ai.nb.model;

import org.springframework.stereotype.Service;

@Service
public class ModelManagementService {
    private final ModelRepository repository;
    private final CredentialCipher cipher;

    public ModelManagementService(ModelRepository repository, CredentialCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public void createModel(CreateModelCommand command) {
        if (command.providerKey() == null || command.providerKey().isBlank()
                || command.modelKey() == null || command.modelKey().isBlank()
                || command.type() == null) {
            throw new IllegalArgumentException("provider, model and type are required");
        }
        repository.save(new ModelConfig(command.providerKey(), command.modelKey(), command.type(),
                cipher.encrypt(command.credentials())));
    }
}
