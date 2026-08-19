package xin.v5ai.nb.model.mapper;

import xin.v5ai.nb.model.ModelProvider;

import java.util.List;

public interface ModelProviderRepository {
    void save(ModelProvider provider);

    ModelProvider findById(Long id);

    ModelProvider findByKey(String providerKey);

    void update(ModelProvider provider);

    void delete(String providerKey);

    List<ModelProvider> list();
}
