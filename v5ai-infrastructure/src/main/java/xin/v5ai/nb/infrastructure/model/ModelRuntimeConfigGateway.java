package xin.v5ai.nb.infrastructure.model;

public interface ModelRuntimeConfigGateway {
    ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId);
}
