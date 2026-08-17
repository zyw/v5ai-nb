package xin.v5ai.nb.common.core.apiKey;

@FunctionalInterface
public interface AgentApiKeyVerifier {
    boolean verify(String agentKey, String apiKey);
}
