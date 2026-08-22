package xin.v5ai.nb.common.agentscope.core.domain;

import xin.v5ai.nb.common.agentscope.enums.AgentStatus;

public record Agent(
        String agentKey,
        String name,
        String description,
        AgentStatus status,
        Long modelId,
        Long publishedVersion,
        String systemPrompt,
        String avatar,
        String greeting,
        String presetQuestions,
        boolean memoryEnabled,
        boolean mcpEnabled,
        boolean skillEnabled,
        boolean webSearchEnabled,
        boolean ragEnabled
) {
    /**
     * 便捷构造器：未设置展示与能力开关字段，使用默认值
     * （字符串为 null、开关为 false）。
     */
    public Agent(String agentKey, String name, String description, AgentStatus status,
                 Long modelId, Long publishedVersion, String systemPrompt) {
        this(agentKey, name, description, status, modelId, publishedVersion, systemPrompt,
                null, null, null, false, false, false, false, false);
    }

    /**
     * 便捷构造器：未设置系统提示词及展示/能力字段。
     */
    public Agent(String agentKey, String name, String description, AgentStatus status,
                 Long modelId, Long publishedVersion) {
        this(agentKey, name, description, status, modelId, publishedVersion, null);
    }
}
