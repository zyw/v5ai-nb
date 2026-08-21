package xin.v5ai.nb.model.domain.vo;

import xin.v5ai.nb.model.ModelProvider;

public record ModelProviderResponse(Long id, String providerKey, String name, String providerType, boolean enabled) {
    static ModelProviderResponse from(ModelProvider provider) {
        return new ModelProviderResponse(provider.id(), provider.providerKey(), provider.name(),
                provider.providerType(), provider.enabled());
    }
}
