package xin.v5ai.nb.agent;

public record CreateAgentCommand(
        String agentKey,
        String name,
        String description,
        Long modelId,
        String systemPrompt,
        String avatar,
        String greeting,
        String presetQuestions,
        Boolean memoryEnabled,
        Boolean mcpEnabled,
        Boolean skillEnabled,
        Boolean webSearchEnabled,
        Boolean ragEnabled
) {
    public CreateAgentCommand(String agentKey, String name, Long modelId) {
        this(agentKey, name, null, modelId, null);
    }

    public CreateAgentCommand(String agentKey, String name, String description, Long modelId) {
        this(agentKey, name, description, modelId, null);
    }

    public CreateAgentCommand(String agentKey, String name, String description, Long modelId, String systemPrompt) {
        this(agentKey, name, description, modelId, systemPrompt,
                null, null, null, null, null, null, null, null);
    }
}
