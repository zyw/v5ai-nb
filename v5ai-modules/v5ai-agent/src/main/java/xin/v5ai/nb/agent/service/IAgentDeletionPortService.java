package xin.v5ai.nb.agent.service;

/**
 * 级联删除端口：删除 AgentDTO 及其全部关联数据（版本/绑定/API Key/会话/运行/用量等）。
 * 由基础设施层实现（跨模块清理依赖 rag/mcp/skill/runtime 等多模块表，见
 * {@code v5ai-infrastructure} 的 AgentDeletionService）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public interface IAgentDeletionPortService {

    /**
     * 级联删除指定 agentKey 的全部关联数据及主表记录。
     *
     * @param agentKey AgentDTO 的 agentKey
     */
    void delete(String agentKey);
}
