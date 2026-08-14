package xin.v5ai.nb.model;

public record UpdateProviderCommand(String providerKey, String name, String providerType, boolean enabled) {
}
