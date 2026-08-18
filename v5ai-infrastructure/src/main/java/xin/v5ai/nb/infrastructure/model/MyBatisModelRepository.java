package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.model.ModelConfig;
import xin.v5ai.nb.model.ModelOption;
import xin.v5ai.nb.model.ModelRepository;
import xin.v5ai.nb.model.ModelType;

import java.util.List;

@Component
public class MyBatisModelRepository implements ModelRepository {
    private final ModelConfigGateway gateway;
    private final ProviderIdResolver providerIdResolver;

    public MyBatisModelRepository(ModelConfigGateway gateway, ProviderIdResolver providerIdResolver) {
        this.gateway = gateway;
        this.providerIdResolver = providerIdResolver;
    }

    @Override
    public void save(ModelConfig model) {
        var entity = toEntity(model);
        entity.setProviderId(providerIdResolver.resolve(model.providerKey()));
        gateway.insertModel(entity);
    }

    @Override
    public ModelConfig findById(Long id) {
        var entity = gateway.selectModelById(id);
        if (entity == null) {
            return null;
        }
        return new ModelConfig(entity.getId(), String.valueOf(entity.getProviderId()), entity.getModelKey(),
                ModelType.valueOf(entity.getModelType()), entity.getCredentialsCiphertext(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }

    @Override
    public void update(ModelConfig model) {
        var entity = toEntity(model);
        entity.setId(model.id());
        entity.setProviderId(providerIdResolver.resolve(model.providerKey()));
        gateway.updateModel(entity);
    }

    @Override
    public void delete(Long id) {
        gateway.deleteModel(id);
    }

    @Override
    public List<ModelConfig> list() {
        return gateway.selectModelRows().stream()
                .map(row -> new ModelConfig(
                        row.getId(),
                        row.getProviderKey(),
                        row.getModelKey(),
                        ModelType.valueOf(row.getModelType()),
                        row.getCredentialsCiphertext(),
                        Boolean.TRUE.equals(row.getEnabled())))
                .toList();
    }

    @Override
    public List<ModelOption> listOptions() {
        return gateway.selectModelOptions().stream()
                .map(row -> new ModelOption(row.getId(), row.getProviderKey(), row.getModelKey(),
                        ModelType.valueOf(row.getModelType())))
                .toList();
    }

    private static ModelConfigEntity toEntity(ModelConfig model) {
        var entity = new ModelConfigEntity();
        entity.setModelKey(model.modelKey());
        entity.setModelType(model.type().name());
        entity.setCredentialsCiphertext(model.credentialsCiphertext());
        entity.setEnabled(model.enabled());
        return entity;
    }
}
