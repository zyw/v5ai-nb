package xin.v5ai.nb.agent;

public record UpdateAgentCommand(String agentKey, String name, String description, Long modelId, String systemPrompt) {
    public UpdateAgentCommand(String agentKey, String name, String description, Long modelId) {
        this(agentKey, name, description, modelId, null);
    }
}
