package xin.v5ai.nb.model.mapper;

import xin.v5ai.nb.model.runtime.config.ModelRuntimeConfig;

public interface ModelRuntimeConfigGateway {
    ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId);

    /**
     * The first enabled EMBEDDING model, or {@code null} when none is configured.
     */
    ModelRuntimeConfig findFirstEmbeddingModel();
}
