package xin.v5ai.nb.model.domain.vo;

import xin.v5ai.nb.model.ModelConfig;
import xin.v5ai.nb.model.ModelType;

public record ModelResponse(
        Long id,
        String providerKey,
        String modelKey,
        ModelType type,
        String credentialsCiphertext,
        boolean enabled
) {
    static ModelResponse from(ModelConfig model) {
        return new ModelResponse(model.id(), model.providerKey(), model.modelKey(), model.type(),
                model.credentialsCiphertext(), model.enabled());
    }
}
