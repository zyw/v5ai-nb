package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.runtime.core.domain.ConversationDTO;

/**
 * 会话仓储端口：负责会话记录的持久化。
 * 实现通常对接数据库表，可替换存储实现。
 */
public interface ConversationService {
    /**
     * 确保会话存在：不存在则创建（幂等操作）。
     * 典型用途：收到新对话请求时先登记会话，再挂载 AgentDTO 状态。
     *
     * @param conversation 要保证存在的会话
     */
    void ensureConversation(ConversationDTO conversation);
}
