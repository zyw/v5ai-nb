package xin.v5ai.nb.common.agentscope.core.service;

/**
 * 会话 Agent 状态的持久化端口（Port）。
 *
 * 负责按会话保存/加载 AgentScope 的 Agent 运行状态（序列化为 JSON）。
 * 默认实现以数据库表 {@code v5ai_agent_state} 作为后端存储。
 * 采用端口-适配器结构，方便替换存储实现（数据库、文件、内存等）。
 */
public interface AgentStateService {
    /**
     * 保存某个会话的 Agent 状态。
     *
     * @param conversationId 会话 ID（唯一标识一段对话）
     * @param stateJson      Agent 运行状态的 JSON 序列化结果
     */
    void save(String conversationId, String stateJson);

    /**
     * 加载某个会话的 Agent 状态。
     *
     * @param conversationId 会话 ID
     * @return 状态的 JSON 字符串；不存在时通常返回 null
     */
    String load(String conversationId);
}
