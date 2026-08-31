package xin.v5ai.nb.common.agentscope.core.domain.dto;

public record ModelRuntimeConfigDTO(
        Long modelId,
        String modelKey,
        String modelType,
        Long providerId,
        String providerKey,
        String providerType,
        String credentialsCiphertext
) {
}
