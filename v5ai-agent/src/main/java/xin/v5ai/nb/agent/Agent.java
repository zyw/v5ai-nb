package xin.v5ai.nb.agent;

public record Agent(
        String agentKey,
        String name,
        String description,
        AgentStatus status,
        Long modelId,
        Long publishedVersion,
        String systemPrompt
) {
    /**
     * 便捷构造器：未设置系统提示词（systemPrompt 为 null，运行时使用默认提示词）。
     */
    public Agent(String agentKey, String name, String description, AgentStatus status,
                 Long modelId, Long publishedVersion) {
        this(agentKey, name, description, status, modelId, publishedVersion, null);
    }
}
