package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Component;

@Component
public class DefaultProviderIdResolver implements ProviderIdResolver {
    private final ModelProviderGateway providerGateway;

    public DefaultProviderIdResolver(ModelProviderGateway providerGateway) {
        this.providerGateway = providerGateway;
    }

    @Override
    public Long resolve(String providerKey) {
        var provider = providerGateway.selectByProviderKey(providerKey);
        if (provider == null || provider.getId() == null) {
            throw new IllegalArgumentException("provider key does not exist: " + providerKey);
        }
        return provider.getId();
    }
}
