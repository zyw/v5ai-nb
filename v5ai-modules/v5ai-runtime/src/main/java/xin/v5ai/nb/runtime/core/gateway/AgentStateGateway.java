package xin.v5ai.nb.runtime.core.gateway;

import xin.v5ai.nb.runtime.core.domain.AgentStateEntity;

/**
 * Agent 状态持久化网关：负责会话级 Agent 状态的写入与读取。
 * 由基础设施层实现（MyBatis），对接 {@code v5ai_agent_state} 表。
 */
public interface AgentStateGateway {
    /**
     * 新增或更新（按 conversationId 幂等写入）某会话的 Agent 状态。
     *
     * @param entity 会话状态实体（含 conversationId 与 stateJson）
     */
    void upsert(AgentStateEntity entity);

    /**
     * 按会话 ID 查询 Agent 状态。
     *
     * @param conversationId 会话 ID
     * @return 状态实体；不存在时返回 {@code null}
     */
    AgentStateEntity selectByConversationId(String conversationId);
}
