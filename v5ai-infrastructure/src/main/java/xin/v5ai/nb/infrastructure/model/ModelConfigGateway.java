package xin.v5ai.nb.infrastructure.model;

import java.util.List;

public interface ModelConfigGateway {
    void insertModel(ModelConfigEntity entity);

    void updateModel(ModelConfigEntity entity);

    void deleteModel(Long modelId);

    ModelConfigEntity selectModelById(Long modelId);

    List<ModelConfigEntity> selectModels();

    List<ModelConfigRow> selectModelRows();

    /** 下拉选项投影：仅 id/provider_key/model_key/model_type，不加载凭据密文。 */
    List<ModelConfigRow> selectModelOptions();
}
