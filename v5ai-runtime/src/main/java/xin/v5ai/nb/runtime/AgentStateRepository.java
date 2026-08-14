package xin.v5ai.nb.runtime;

/**
 * Port for persisting and loading the AgentScope agent state of a conversation.
 * Backed by the {@code v5ai_agent_state} table in the default implementation.
 */
public interface AgentStateRepository {
    void save(String conversationId, String stateJson);

    String load(String conversationId);
}
