package xin.v5ai.nb.runtime;

import java.util.List;

/**
 * 消息仓储端口：负责会话消息的持久化。
 */
public interface MessageRepository {
    /**
     * 保存一条消息（用户提问或助手回答）。
     *
     * @param message 要保存的消息
     */
    void save(Message message);

    /**
     * 查询某个会话的全部历史消息（通常按时间正序返回）。
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表
     */
    List<Message> findByConversationId(String conversationId);
}
