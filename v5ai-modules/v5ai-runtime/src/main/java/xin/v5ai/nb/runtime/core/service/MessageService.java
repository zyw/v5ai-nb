package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 消息仓储端口：负责会话消息的持久化。
 */
public interface MessageService {
    /**
     * 保存一条消息（用户提问或助手回答）。
     *
     * @param message 要保存的消息
     */
    boolean save(SessionMessage message);

    /**
     * 查询某个会话的全部历史消息（通常按时间正序返回）。
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表
     */
    List<SessionMessage> findByConversationId(String conversationId);

    /**
     * 批量取每个会话的**首条用户消息**内容，用于未命名会话的列表展示兜底。
     *
     * @param conversationIds 会话 ID 集合
     * @return 会话 ID -&gt; 首条用户消息内容；无用户消息的会话不出现在结果里
     */
    Map<String, String> findFirstUserMessageByConversationIds(Collection<String> conversationIds);
}
