package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.model.ModelConfig;
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
        var entity = new ModelConfigEntity();
        entity.setProviderId(providerIdResolver.resolve(model.providerKey()));
        entity.setModelKey(model.modelKey());
        entity.setModelType(model.type().name());
        entity.setCredentialsCiphertext(model.credentialsCiphertext());
        gateway.insertModel(entity);
    }

    @Override
    public List<ModelConfig> list() {
        return gateway.selectModels().stream()
                .map(entity -> new ModelConfig(
                        String.valueOf(entity.getProviderId()),
                        entity.getModelKey(),
                        ModelType.valueOf(entity.getModelType()),
                        entity.getCredentialsCiphertext()))
                .toList();
    }
}
