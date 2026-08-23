package xin.v5ai.nb.infrastructure.runtime;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.Conversation;
import xin.v5ai.nb.common.agentscope.core.service.ConversationService;

@Repository
public class MyBatisConversationRepository implements ConversationService {
    private final RuntimeConversationGateway gateway;

    public MyBatisConversationRepository(RuntimeConversationGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void ensureConversation(Conversation conversation) {
        var entity = new RuntimeConversationEntity();
        entity.setConversationId(conversation.conversationId());
        entity.setAgentKey(conversation.agentKey());
        gateway.upsertConversation(entity);
    }
}
