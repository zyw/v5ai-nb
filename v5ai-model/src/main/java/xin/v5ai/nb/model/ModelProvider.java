package xin.v5ai.nb.model;

public record ModelProvider(Long id, String providerKey, String name, String providerType, boolean enabled) {
    public ModelProvider(String providerKey, String name, String providerType, boolean enabled) {
        this(null, providerKey, name, providerType, enabled);
    }

    public ModelProvider withId(Long id) {
        return new ModelProvider(id, providerKey, name, providerType, enabled);
    }
}
