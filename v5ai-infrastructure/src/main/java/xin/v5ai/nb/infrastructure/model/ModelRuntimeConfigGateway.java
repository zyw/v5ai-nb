package xin.v5ai.nb.infrastructure.model;

public interface ModelRuntimeConfigGateway {
    ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId);

    /**
     * The first enabled EMBEDDING model, or {@code null} when none is configured.
     */
    ModelRuntimeConfig findFirstEmbeddingModel();
}
