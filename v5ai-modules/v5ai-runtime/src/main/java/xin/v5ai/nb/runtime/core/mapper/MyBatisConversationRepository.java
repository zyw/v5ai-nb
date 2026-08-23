package xin.v5ai.nb.runtime.core.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.Conversation;
import xin.v5ai.nb.common.agentscope.core.service.ConversationService;
import xin.v5ai.nb.runtime.core.gateway.RuntimeConversationGateway;
import xin.v5ai.nb.runtime.core.domain.RuntimeConversationEntity;

@Repository
@RequiredArgsConstructor
public class MyBatisConversationRepository implements ConversationService {

    private final RuntimeConversationGateway gateway;

    @Override
    public void ensureConversation(Conversation conversation) {
        var entity = new RuntimeConversationEntity();
        entity.setConversationId(conversation.conversationId());
        entity.setAgentKey(conversation.agentKey());
        gateway.upsertConversation(entity);
    }
}
