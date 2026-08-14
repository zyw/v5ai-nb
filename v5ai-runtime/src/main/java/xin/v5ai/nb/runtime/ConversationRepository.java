package xin.v5ai.nb.runtime;

/**
 * 会话仓储端口：负责会话记录的持久化。
 * 实现通常对接数据库表，可替换存储实现。
 */
public interface ConversationRepository {
    /**
     * 确保会话存在：不存在则创建（幂等操作）。
     * 典型用途：收到新对话请求时先登记会话，再挂载 Agent 状态。
     *
     * @param conversation 要保证存在的会话
     */
    void ensureConversation(Conversation conversation);
}
