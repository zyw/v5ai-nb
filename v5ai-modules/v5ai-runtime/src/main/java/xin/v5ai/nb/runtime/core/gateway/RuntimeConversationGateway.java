package xin.v5ai.nb.runtime.core.gateway;

import xin.v5ai.nb.runtime.core.domain.RuntimeConversationEntity;

public interface RuntimeConversationGateway {
    void upsertConversation(RuntimeConversationEntity entity);
}
