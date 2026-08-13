package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.model.ModelProvider;
import xin.v5ai.nb.model.ModelProviderRepository;

import java.util.List;

@Repository
public class MyBatisModelProviderRepository implements ModelProviderRepository {
    private final ModelProviderGateway gateway;

    public MyBatisModelProviderRepository(ModelProviderGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(ModelProvider provider) {
        gateway.insertProvider(toEntity(provider));
    }

    @Override
    public ModelProvider findByKey(String providerKey) {
        return toDomain(gateway.selectByProviderKey(providerKey));
    }

    @Override
    public List<ModelProvider> list() {
        return gateway.selectProviders().stream()
                .map(MyBatisModelProviderRepository::toDomain)
                .toList();
    }

    private static ModelProviderEntity toEntity(ModelProvider provider) {
        var entity = new ModelProviderEntity();
        entity.setProviderKey(provider.providerKey());
        entity.setName(provider.name());
        entity.setProviderType(provider.providerType());
        entity.setEnabled(provider.enabled());
        return entity;
    }

    private static ModelProvider toDomain(ModelProviderEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ModelProvider(entity.getProviderKey(), entity.getName(), entity.getProviderType(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }
}
