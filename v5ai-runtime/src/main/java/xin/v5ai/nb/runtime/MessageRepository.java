package xin.v5ai.nb.runtime;

import java.util.List;

public interface MessageRepository {
    void save(Message message);

    List<Message> findByConversationId(String conversationId);
}
