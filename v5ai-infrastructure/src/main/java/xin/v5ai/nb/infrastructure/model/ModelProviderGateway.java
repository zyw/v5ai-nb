package xin.v5ai.nb.infrastructure.model;

import java.util.List;

public interface ModelProviderGateway {
    void insertProvider(ModelProviderEntity entity);

    void updateProvider(ModelProviderEntity entity);

    void deleteProvider(String providerKey);

    ModelProviderEntity selectProviderById(Long id);

    ModelProviderEntity selectByProviderKey(String providerKey);

    List<ModelProviderEntity> selectProviders();
}
