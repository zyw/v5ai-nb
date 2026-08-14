package xin.v5ai.nb.infrastructure.runtime;

import java.util.List;

public interface RuntimeMessageGateway {
    void insertMessage(RuntimeMessageEntity entity);

    List<RuntimeMessageEntity> selectMessagesByConversationId(String conversationId);
}
