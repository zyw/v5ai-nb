package xin.v5ai.nb.rag;

public interface AgentKnowledgeBindingRepository {
    void save(String agentKey, java.util.List<Long> knowledgeBaseIds);

    java.util.List<Long> findByAgentKey(String agentKey);

    void delete(String agentKey);
}
