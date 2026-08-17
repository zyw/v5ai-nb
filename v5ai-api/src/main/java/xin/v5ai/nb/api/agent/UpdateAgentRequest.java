package xin.v5ai.nb.api.agent;

public record UpdateAgentRequest(
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
    public UpdateAgentRequest(String name, String description, Long modelId, String systemPrompt) {
        this(name, description, modelId, systemPrompt,
                null, null, null, null, null, null, null, null);
    }
}
