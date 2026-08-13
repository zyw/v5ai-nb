package xin.v5ai.nb.infrastructure.model;

import java.util.List;

public interface ModelConfigGateway {
    void insertModel(ModelConfigEntity entity);

    List<ModelConfigEntity> selectModels();
}
