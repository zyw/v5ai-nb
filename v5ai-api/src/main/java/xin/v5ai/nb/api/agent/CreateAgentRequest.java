package xin.v5ai.nb.api.agent;

public record CreateAgentRequest(
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
    public CreateAgentRequest(String agentKey, String name, String description, Long modelId, String systemPrompt) {
        this(agentKey, name, description, modelId, systemPrompt,
                null, null, null, null, null, null, null, null);
    }
}
