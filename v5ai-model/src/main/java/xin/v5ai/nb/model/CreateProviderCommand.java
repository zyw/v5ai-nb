package xin.v5ai.nb.model;

public record CreateProviderCommand(String providerKey, String name, String providerType, boolean enabled) {
}
