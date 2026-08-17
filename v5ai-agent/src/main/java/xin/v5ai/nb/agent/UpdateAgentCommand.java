package xin.v5ai.nb.agent;

public record UpdateAgentCommand(
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
    public UpdateAgentCommand(String agentKey, String name, String description, Long modelId) {
        this(agentKey, name, description, modelId, null);
    }

    public UpdateAgentCommand(String agentKey, String name, String description, Long modelId, String systemPrompt) {
        this(agentKey, name, description, modelId, systemPrompt,
                null, null, null, null, null, null, null, null);
    }
}
