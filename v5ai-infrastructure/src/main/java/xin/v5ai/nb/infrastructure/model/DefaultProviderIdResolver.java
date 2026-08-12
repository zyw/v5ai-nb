package xin.v5ai.nb.infrastructure.model;

import org.springframework.stereotype.Component;

@Component
public class DefaultProviderIdResolver implements ProviderIdResolver {
    @Override
    public Long resolve(String providerKey) {
        try {
            return Long.valueOf(providerKey);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("provider key must resolve to a provider id in phase 1", e);
        }
    }
}
