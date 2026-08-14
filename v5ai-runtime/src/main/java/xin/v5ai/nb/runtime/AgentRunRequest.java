package xin.v5ai.nb.runtime;

import java.util.List;

public record AgentRunRequest(
        String appKey,
        String conversationId,
        String query,
        String ragContext,
        List<Message> history
) {
    public AgentRunRequest(String appKey, String conversationId, String query) {
        this(appKey, conversationId, query, null, List.of());
    }

    public AgentRunRequest withRagContext(String context) {
        return new AgentRunRequest(appKey, conversationId, query, context, history);
    }

    public AgentRunRequest withHistory(List<Message> history) {
        return new AgentRunRequest(appKey, conversationId, query, ragContext, history);
    }
}
