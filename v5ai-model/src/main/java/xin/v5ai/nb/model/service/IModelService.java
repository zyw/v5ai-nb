package xin.v5ai.nb.model.service;

import xin.v5ai.nb.model.*;

import java.util.List;

public interface IModelService {
    ModelConfig createModel(CreateModelCommand command);

    ModelConfig updateModel(UpdateModelCommand command);

    void deleteModel(Long id);

    ModelConfig findModel(Long id);

    List<ModelConfig> listModels();

    List<ModelOption> listModelOptions();

    ConnectionTestResult testConnection(Long modelId);
}
