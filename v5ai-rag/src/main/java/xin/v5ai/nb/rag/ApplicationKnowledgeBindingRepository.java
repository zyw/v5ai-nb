package xin.v5ai.nb.rag;

public interface ApplicationKnowledgeBindingRepository {
    void save(String appKey, java.util.List<Long> knowledgeBaseIds);

    java.util.List<Long> findByAppKey(String appKey);

    void delete(String appKey);
}
