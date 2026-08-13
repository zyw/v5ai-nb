package xin.v5ai.nb.infrastructure.model;

public record ModelRuntimeConfig(
        Long modelId,
        String modelKey,
        String modelType,
        Long providerId,
        String providerKey,
        String providerType,
        String credentialsCiphertext
) {
}
