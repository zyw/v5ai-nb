package xin.v5ai.nb.model;

/**
 * Port for testing a configured model connection. Implementations may verify the
 * endpoint and credentials by issuing a minimal request against the model provider.
 */
public interface ModelConnectionTester {
    boolean test(Long modelId);
}
