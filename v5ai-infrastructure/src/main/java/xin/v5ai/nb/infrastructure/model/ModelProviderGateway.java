package xin.v5ai.nb.infrastructure.model;

import java.util.List;

public interface ModelProviderGateway {
    void insertProvider(ModelProviderEntity entity);

    ModelProviderEntity selectByProviderKey(String providerKey);

    List<ModelProviderEntity> selectProviders();
}
