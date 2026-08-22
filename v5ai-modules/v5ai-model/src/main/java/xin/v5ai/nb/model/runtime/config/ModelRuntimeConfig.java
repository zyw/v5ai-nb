package xin.v5ai.nb.model.runtime.config;

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
