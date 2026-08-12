package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Component;
import xin.v5ai.nb.model.ModelConfig;
import xin.v5ai.nb.model.ModelRepository;

@Component
public class MyBatisModelRepository implements ModelRepository {
    private final ModelConfigMapper mapper;

    public MyBatisModelRepository(ModelConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ModelConfig model) {
        var entity = new ModelConfigEntity();
        entity.setModelKey(model.modelKey());
        entity.setModelType(model.type().name());
        entity.setCredentialsCiphertext(model.credentialsCiphertext());
        mapper.insert(entity);
    }
}
