package xin.v5ai.nb.infrastructure.runtime;

public interface AgentStateGateway {
    void upsert(AgentStateEntity entity);

    AgentStateEntity selectByConversationId(String conversationId);
}
