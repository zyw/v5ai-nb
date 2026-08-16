package xin.v5ai.nb.api.agent;

public record UpdateAgentRequest(String name, String description, Long modelId, String systemPrompt) {
}
