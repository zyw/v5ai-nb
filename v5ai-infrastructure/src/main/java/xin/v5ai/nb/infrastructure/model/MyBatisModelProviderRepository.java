package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.model.ModelProvider;
import xin.v5ai.nb.model.mapper.ModelProviderMapper;

import java.util.List;

@Repository
public class MyBatisModelProviderRepository implements ModelProviderMapper {
    private final ModelProviderGateway gateway;

    public MyBatisModelProviderRepository(ModelProviderGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(ModelProvider provider) {
        gateway.insertProvider(toEntity(provider));
    }

    @Override
    public ModelProvider findById(Long id) {
        return toDomain(gateway.selectProviderById(id));
    }

    @Override
    public ModelProvider findByKey(String providerKey) {
        return toDomain(gateway.selectByProviderKey(providerKey));
    }

    @Override
    public void update(ModelProvider provider) {
        var entity = toEntity(provider);
        entity.setId(provider.id());
        gateway.updateProvider(entity);
    }

    @Override
    public void delete(String providerKey) {
        gateway.deleteProvider(providerKey);
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
        return new ModelProvider(entity.getId(), entity.getProviderKey(), entity.getName(), entity.getProviderType(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }
}
