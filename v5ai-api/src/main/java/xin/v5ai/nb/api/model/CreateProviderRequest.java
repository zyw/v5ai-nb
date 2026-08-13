package xin.v5ai.nb.api.model;

public record CreateProviderRequest(String providerKey, String name, String providerType, boolean enabled) {
}
