package xin.v5ai.nb.api.agent;

public record CreateAgentRequest(String agentKey, String name, String description, Long modelId, String systemPrompt) {
}
