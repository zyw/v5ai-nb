package xin.v5ai.nb.infrastructure.model;

import java.util.List;

public interface ModelConfigGateway {
    void insertModel(ModelConfigEntity entity);

    void updateModel(ModelConfigEntity entity);

    void deleteModel(Long modelId);

    ModelConfigEntity selectModelById(Long modelId);

    List<ModelConfigEntity> selectModels();

    List<ModelConfigRow> selectModelRows();
}
