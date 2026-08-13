package xin.v5ai.nb.model;

import java.util.List;

public interface ModelProviderRepository {
    void save(ModelProvider provider);

    ModelProvider findByKey(String providerKey);

    List<ModelProvider> list();
}
