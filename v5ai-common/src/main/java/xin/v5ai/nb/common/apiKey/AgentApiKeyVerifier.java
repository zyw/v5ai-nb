package xin.v5ai.nb.common.apiKey;

@FunctionalInterface
public interface AgentApiKeyVerifier {
    boolean verify(String agentKey, String apiKey);
}
