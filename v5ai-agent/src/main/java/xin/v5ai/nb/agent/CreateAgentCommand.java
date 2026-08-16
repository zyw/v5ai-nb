package xin.v5ai.nb.agent;

public record CreateAgentCommand(String agentKey, String name, String description, Long modelId, String systemPrompt) {
    public CreateAgentCommand(String agentKey, String name, Long modelId) {
        this(agentKey, name, null, modelId, null);
    }

    public CreateAgentCommand(String agentKey, String name, String description, Long modelId) {
        this(agentKey, name, description, modelId, null);
    }
}
