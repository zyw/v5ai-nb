package xin.v5ai.nb.rag.service;

import java.util.List;

/**
 * AgentDTO 与知识库绑定关系的仓储端口。
 * 记录一个 AgentDTO 应用绑定了哪些知识库，运行时据此构建 RAG 检索上下文。
 */
public interface IAgentKnowledgeBindingService {
    /**
     * 保存某 AgentDTO 的知识库绑定（整体覆盖该 AgentDTO 的绑定集合）。
     *
     * @param agentKey         AgentDTO 标识（运行期 appKey）
     * @param knowledgeBaseIds 绑定的知识库 ID 列表
     */
    void save(String agentKey, List<Long> knowledgeBaseIds);

    /**
     * 查询某 AgentDTO 绑定的全部知识库 ID。
     *
     * @param agentKey AgentDTO 标识
     * @return 知识库 ID 列表
     */
    List<Long> findByAgentKey(String agentKey);

    /**
     * 删除某 AgentDTO 的全部知识库绑定。
     *
     * @param agentKey AgentDTO 标识
     */
    void delete(String agentKey);
}
