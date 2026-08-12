package xin.v5ai.nb.infrastructure.model;

@FunctionalInterface
public interface ProviderIdResolver {
    Long resolve(String providerKey);
}
