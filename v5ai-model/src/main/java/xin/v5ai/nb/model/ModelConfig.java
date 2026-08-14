package xin.v5ai.nb.model;

public record ModelConfig(
        Long id,
        String providerKey,
        String modelKey,
        ModelType type,
        String credentialsCiphertext,
        boolean enabled
) {
    public ModelConfig(String providerKey, String modelKey, ModelType type, String credentialsCiphertext) {
        this(null, providerKey, modelKey, type, credentialsCiphertext, true);
    }

    public ModelConfig withId(Long id) {
        return new ModelConfig(id, providerKey, modelKey, type, credentialsCiphertext, enabled);
    }

    public ModelConfig withCredentials(String credentialsCiphertext) {
        return new ModelConfig(id, providerKey, modelKey, type, credentialsCiphertext, enabled);
    }
}
