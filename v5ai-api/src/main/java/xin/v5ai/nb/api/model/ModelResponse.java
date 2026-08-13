package xin.v5ai.nb.api.model;

import xin.v5ai.nb.model.ModelConfig;
import xin.v5ai.nb.model.ModelType;

public record ModelResponse(String providerKey, String modelKey, ModelType type, String credentialsCiphertext) {
    static ModelResponse from(ModelConfig model) {
        return new ModelResponse(model.providerKey(), model.modelKey(), model.type(), model.credentialsCiphertext());
    }
}
