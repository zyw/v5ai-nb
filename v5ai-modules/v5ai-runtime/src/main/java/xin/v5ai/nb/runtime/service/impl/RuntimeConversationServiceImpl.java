package xin.v5ai.nb.runtime.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.runtime.core.domain.ConversationDTO;
import xin.v5ai.nb.runtime.core.service.ConversationService;
import xin.v5ai.nb.runtime.domain.RuntimeConversation;
import xin.v5ai.nb.runtime.mapper.RuntimeConversationMapper;
import xin.v5ai.nb.runtime.service.IRuntimeConversationService;

@Service
@RequiredArgsConstructor
public class RuntimeConversationServiceImpl implements IRuntimeConversationService, ConversationService {

    private final RuntimeConversationMapper baseMapper;

    @Override
    public void ensureConversation(ConversationDTO conversation) {
        var entity = new RuntimeConversation();
        entity.setId(conversation.conversationId());
        entity.setAgentKey(conversation.agentKey());
        baseMapper.insertOrUpdate(entity);
    }
}
