package xin.v5ai.nb.common.apiKey;

@FunctionalInterface
public interface ApplicationApiKeyVerifier {
    boolean verify(String appKey, String apiKey);
}
