package xin.v5ai.nb.api.agent;

import xin.v5ai.nb.agent.Agent;
import xin.v5ai.nb.agent.AgentStatus;

public record AgentResponse(
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
    static AgentResponse from(Agent agent) {
        return new AgentResponse(agent.agentKey(), agent.name(), agent.description(),
                agent.status(), agent.modelId(), agent.publishedVersion(), agent.systemPrompt(),
                agent.avatar(), agent.greeting(), agent.presetQuestions(),
                agent.memoryEnabled(), agent.mcpEnabled(), agent.skillEnabled(),
                agent.webSearchEnabled(), agent.ragEnabled());
    }
}
