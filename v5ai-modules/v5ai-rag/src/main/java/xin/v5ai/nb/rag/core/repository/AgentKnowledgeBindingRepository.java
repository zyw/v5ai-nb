//package xin.v5ai.nb.rag.core.repository;
//
//import java.util.List;
//
///**
// * Agent 与知识库绑定关系的仓储端口。
// * 记录一个 Agent 应用绑定了哪些知识库，运行时据此构建 RAG 检索上下文。
// */
//public interface AgentKnowledgeBindingRepository {
//    /**
//     * 保存某 Agent 的知识库绑定（整体覆盖该 Agent 的绑定集合）。
//     *
//     * @param agentKey         Agent 标识（运行期 appKey）
//     * @param knowledgeBaseIds 绑定的知识库 ID 列表
//     */
//    void save(String agentKey, List<Long> knowledgeBaseIds);
//
//    /**
//     * 查询某 Agent 绑定的全部知识库 ID。
//     *
//     * @param agentKey Agent 标识
//     * @return 知识库 ID 列表
//     */
//    List<Long> findByAgentKey(String agentKey);
//
//    /**
//     * 删除某 Agent 的全部知识库绑定。
//     *
//     * @param agentKey Agent 标识
//     */
//    void delete(String agentKey);
//}
