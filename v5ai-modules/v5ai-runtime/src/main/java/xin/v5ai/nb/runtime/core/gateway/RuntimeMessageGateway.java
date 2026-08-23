package xin.v5ai.nb.runtime.core.gateway;

import xin.v5ai.nb.runtime.core.domain.RuntimeMessageEntity;

import java.util.List;

/**
 * 运行时消息持久化网关：负责会话消息的写入与历史查询。
 * 由基础设施层实现（MyBatis），对接 {@code v5ai_message} 表。
 */
public interface RuntimeMessageGateway {
    /**
     * 插入一条会话消息（用户提问或助手回答）。
     *
     * @param entity 消息实体（conversationId/agentKey/role/content）
     */
    void insertMessage(RuntimeMessageEntity entity);

    /**
     * 查询某会话的全部历史消息（按时间正序返回）。
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表
     */
    List<RuntimeMessageEntity> selectMessagesByConversationId(String conversationId);
}
