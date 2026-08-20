package xin.v5ai.nb.model.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.model.CreateProviderCommand;
import xin.v5ai.nb.model.ModelProvider;
import xin.v5ai.nb.model.mapper.ModelMapper;
import xin.v5ai.nb.model.UpdateProviderCommand;
import xin.v5ai.nb.model.mapper.ModelProviderMapper;
import xin.v5ai.nb.model.service.IModelProviderService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl implements IModelProviderService {

    private final ModelMapper repository;
    private final ModelProviderMapper providerRepository;

    @Override
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

    @Override
    public ModelProvider updateProvider(UpdateProviderCommand command) {
        var existing = providerRepository == null ? null : providerRepository.findByKey(command.providerKey());
        if (existing == null) {
            throw new IllegalArgumentException("provider does not exist: " + command.providerKey());
        }
        var provider = new ModelProvider(existing.id(), command.providerKey(), command.name(), command.providerType(), command.enabled());
        providerRepository.update(provider);
        return provider;
    }

    @Override
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

    @Override
    public List<ModelProvider> listProviders() {
        return providerRepository == null ? List.of() : providerRepository.list();
    }

    @Override
    public PageResult<ModelProvider> queryPageList(PageQuery pageQuery) {
        // TODO implement queryPageList
        return null;
    }
}
